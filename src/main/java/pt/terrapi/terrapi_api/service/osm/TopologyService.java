package pt.terrapi.terrapi_api.service.osm;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Builds the routable car graph from the raw {@code routing_edges} table plus the {@code way_nodes}
 * node references emitted by the osm2pgsql flex style.
 *
 * <p>Ways are noded at <b>shared OSM nodes</b> (the true intersections) rather than by computing
 * geometry intersections: this is O(n), and the OSM node id <i>is</i> the graph vertex id, so
 * {@code source}/{@code target} come for free (no vertex extraction, no spatial join). Per-edge
 * travel time ({@code cost_s}/{@code reverse_cost_s}) is computed inline from class speed defaults,
 * {@code maxspeed} and {@code oneway}. Parameter-free and re-runnable — every run drops and rebuilds
 * the derived tables. Vertex/edge endpoint ids are OSM node ids, hence {@code bigint}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopologyService {

    /** Class-default speed (km/h), used when maxspeed is absent or zero; never returns 0. */
    private static final String SPEED_KMH = """
            coalesce(NULLIF(c.maxspeed_kmh, 0), CASE c.highway
                WHEN 'motorway' THEN 110 WHEN 'motorway_link' THEN 60
                WHEN 'trunk' THEN 90 WHEN 'trunk_link' THEN 50
                WHEN 'primary' THEN 80 WHEN 'primary_link' THEN 45
                WHEN 'secondary' THEN 70 WHEN 'secondary_link' THEN 40
                WHEN 'tertiary' THEN 60 WHEN 'tertiary_link' THEN 35
                WHEN 'unclassified' THEN 50 WHEN 'residential' THEN 40
                WHEN 'living_street' THEN 10 WHEN 'service' THEN 20
                WHEN 'road' THEN 40 WHEN 'track' THEN 20
                ELSE 30 END)""";

    private final JdbcTemplate jdbcTemplate;

    private record Step(String description, String sql) {}

    public void rebuildCar() {
        long t0 = System.currentTimeMillis();
        for (Step step : steps()) {
            long s0 = System.currentTimeMillis();
            log.info("[topology] {} ...", step.description());
            jdbcTemplate.execute(step.sql());
            log.info("[topology] {} done in {} ms", step.description(), System.currentTimeMillis() - s0);
        }
        Long edges = jdbcTemplate.queryForObject("SELECT count(*) FROM car_edges_noded", Long.class);
        Long verts = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM car_edges_noded_vertices_pgr", Long.class);
        log.info("[topology] car graph rebuilt — {} edges, {} vertices in {} ms",
                edges, verts, System.currentTimeMillis() - t0);
    }

    private List<Step> steps() {
        return List.of(
                new Step("drop previous derived tables", """
                        DROP TABLE IF EXISTS car_edges_noded_vertices_pgr;
                        DROP TABLE IF EXISTS car_edges_noded;
                        DROP TABLE IF EXISTS car_edges;
                        """),
                new Step("build car-routable edge subset", """
                        CREATE TABLE car_edges AS
                        SELECT osm_id, highway, oneway, junction, maxspeed_kmh, geom
                        FROM routing_edges
                        WHERE highway IN ('motorway','motorway_link','trunk','trunk_link',
                                          'primary','primary_link','secondary','secondary_link',
                                          'tertiary','tertiary_link','unclassified','residential',
                                          'living_street','service','road','track')
                          AND coalesce(motor_vehicle,'') NOT IN ('no','private')
                          AND coalesce(access,'') NOT IN ('no','private')
                          AND geom IS NOT NULL;
                        CREATE INDEX idx_car_edges_osmid ON car_edges(osm_id);
                        ANALYZE car_edges;
                        """),
                new Step("node at shared OSM nodes + cost", """
                        SET work_mem = '512MB';
                        CREATE TABLE car_edges_noded AS
                        WITH wn AS (
                            SELECT w.way_id, w.seq, w.node_id
                            FROM way_nodes w
                            JOIN car_edges c ON c.osm_id = w.way_id
                        ),
                        endpoints AS (
                            SELECT way_id, min(seq) AS min_seq, max(seq) AS max_seq
                            FROM wn GROUP BY way_id
                        ),
                        shared AS (
                            SELECT node_id FROM wn GROUP BY node_id HAVING count(*) >= 2
                        ),
                        splits AS (
                            SELECT w.way_id, w.seq, w.node_id
                            FROM wn w
                            JOIN endpoints e ON e.way_id = w.way_id
                            WHERE w.seq = e.min_seq OR w.seq = e.max_seq
                               OR w.node_id IN (SELECT node_id FROM shared)
                        ),
                        seg AS (
                            SELECT row_number() OVER () AS id, way_id,
                                   node_id::bigint AS source, seq AS s1,
                                   lead(node_id) OVER (PARTITION BY way_id ORDER BY seq)::bigint AS target,
                                   lead(seq)     OVER (PARTITION BY way_id ORDER BY seq) AS s2
                            FROM splits
                        ),
                        pts AS (
                            SELECT c.osm_id AS way_id, (dp.path)[1] AS seq, dp.geom AS pt
                            FROM car_edges c CROSS JOIN LATERAL ST_DumpPoints(c.geom) AS dp
                        ),
                        geo AS (
                            SELECT s.id, s.way_id, s.source, s.target,
                                   max(c.oneway) AS oneway, max(c.junction) AS junction,
                                   max(%s) AS speed_kmh,
                                   ST_MakeLine(p.pt ORDER BY p.seq) AS geom
                            FROM seg s
                            JOIN pts p ON p.way_id = s.way_id AND p.seq BETWEEN s.s1 AND s.s2
                            JOIN car_edges c ON c.osm_id = s.way_id
                            WHERE s.s2 IS NOT NULL
                            GROUP BY s.id, s.way_id, s.source, s.target
                        )
                        SELECT id, way_id, source, target, geom,
                               CASE WHEN oneway = '-1' THEN -1 ELSE t END AS cost_s,
                               CASE WHEN oneway IN ('yes','true','1') OR junction = 'roundabout' THEN -1
                                    ELSE t END AS reverse_cost_s
                        FROM (
                            SELECT id, way_id, source, target, oneway, junction, geom,
                                   ST_Length(geom::geography) / (GREATEST(speed_kmh,1) / 3.6) AS t
                            FROM geo
                            WHERE ST_GeometryType(geom) = 'ST_LineString'
                              AND ST_NPoints(geom) >= 2
                              AND source <> target
                              AND ST_Length(geom) > 0
                        ) q;
                        ALTER TABLE car_edges_noded ADD PRIMARY KEY (id);
                        ANALYZE car_edges_noded;
                        """.formatted(SPEED_KMH)),
                new Step("build vertices from node endpoints", """
                        CREATE TABLE car_edges_noded_vertices_pgr AS
                        SELECT DISTINCT ON (id) id, the_geom FROM (
                            SELECT source AS id, ST_StartPoint(geom) AS the_geom FROM car_edges_noded
                            UNION ALL
                            SELECT target AS id, ST_EndPoint(geom) AS the_geom FROM car_edges_noded
                        ) u;
                        ALTER TABLE car_edges_noded_vertices_pgr ADD PRIMARY KEY (id);
                        CREATE INDEX idx_car_vertices_geom
                            ON car_edges_noded_vertices_pgr USING gist(the_geom);
                        """));
    }
}
