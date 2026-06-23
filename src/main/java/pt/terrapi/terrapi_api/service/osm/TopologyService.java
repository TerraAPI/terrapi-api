package pt.terrapi.terrapi_api.service.osm;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Builds the routable car graph from the raw {@code routing_edges} table using pgRouting.
 *
 * <p>The pipeline filters car-usable highways, nodes the network in PostGIS (splits ways at
 * intersection points so through-roads connect), derives the vertices with {@code pgr_extractVertices}
 * (the current, non-deprecated topology helper — {@code pgr_nodeNetwork}/{@code pgr_createTopology}
 * were removed in pgRouting 3.8), assigns {@code source}/{@code target}, and computes per-edge travel
 * time ({@code cost_s}/{@code reverse_cost_s}) from class speed defaults, {@code maxspeed} and
 * {@code oneway}. It is parameter-free and re-runnable: every run drops and rebuilds the derived
 * tables. The PostGIS noding step is the slow part — fine for a rare, post-import action.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopologyService {

    /** Snap tolerance (degrees, ~1mm) so intersection points sit exactly on the line for ST_Split. */
    private static final String SNAP_TOL = "0.00000001";

    private static final String SPEED_KMH = """
            coalesce(maxspeed_kmh, CASE highway
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
                        SELECT row_number() OVER () AS id,
                               highway, oneway, junction, maxspeed_kmh, geom
                        FROM routing_edges
                        WHERE highway IN ('motorway','motorway_link','trunk','trunk_link',
                                          'primary','primary_link','secondary','secondary_link',
                                          'tertiary','tertiary_link','unclassified','residential',
                                          'living_street','service','road','track')
                          AND coalesce(motor_vehicle,'') NOT IN ('no','private')
                          AND coalesce(access,'') NOT IN ('no','private')
                          AND geom IS NOT NULL;
                        ALTER TABLE car_edges ADD PRIMARY KEY (id);
                        CREATE INDEX idx_car_edges_geom ON car_edges USING gist(geom);
                        """),
                new Step("node the network at intersections (PostGIS)", """
                        CREATE TABLE car_edges_noded AS
                        WITH inter AS (
                            SELECT a.id AS old_id, ST_Collect(pt.geom) AS blade
                            FROM car_edges a
                            JOIN car_edges b
                              ON a.id <> b.id AND ST_Intersects(a.geom, b.geom)
                            CROSS JOIN LATERAL (
                                SELECT (ST_Dump(ST_Intersection(a.geom, b.geom))).geom AS geom
                            ) pt
                            WHERE ST_GeometryType(pt.geom) = 'ST_Point'
                            GROUP BY a.id
                        ),
                        split AS (
                            SELECT a.id AS old_id, a.highway, a.oneway, a.junction, a.maxspeed_kmh,
                                   (ST_Dump(ST_Split(ST_Snap(a.geom, i.blade, %s), i.blade))).geom AS geom
                            FROM car_edges a
                            JOIN inter i ON a.id = i.old_id
                            UNION ALL
                            SELECT a.id, a.highway, a.oneway, a.junction, a.maxspeed_kmh, a.geom
                            FROM car_edges a
                            WHERE NOT EXISTS (SELECT 1 FROM inter i WHERE i.old_id = a.id)
                        )
                        SELECT row_number() OVER () AS id,
                               old_id, highway, oneway, junction, maxspeed_kmh, geom
                        FROM split
                        WHERE ST_GeometryType(geom) = 'ST_LineString'
                          AND ST_NPoints(geom) >= 2
                          AND ST_Length(geom) > 0;
                        ALTER TABLE car_edges_noded ADD PRIMARY KEY (id);
                        CREATE INDEX idx_car_edges_noded_geom ON car_edges_noded USING gist(geom);
                        """.formatted(SNAP_TOL)),
                new Step("extract vertices", """
                        CREATE TABLE car_edges_noded_vertices_pgr AS
                        SELECT id, geom AS the_geom
                        FROM pgr_extractVertices('SELECT id, geom FROM car_edges_noded');
                        ALTER TABLE car_edges_noded_vertices_pgr ADD PRIMARY KEY (id);
                        CREATE INDEX idx_car_vertices_geom
                            ON car_edges_noded_vertices_pgr USING gist(the_geom);
                        """),
                new Step("assign source/target + cost columns", """
                        ALTER TABLE car_edges_noded
                            ADD COLUMN source integer,
                            ADD COLUMN target integer,
                            ADD COLUMN cost_s double precision,
                            ADD COLUMN reverse_cost_s double precision;
                        UPDATE car_edges_noded e SET source = v.id
                        FROM car_edges_noded_vertices_pgr v
                        WHERE ST_DWithin(v.the_geom, ST_StartPoint(e.geom), 0);
                        UPDATE car_edges_noded e SET target = v.id
                        FROM car_edges_noded_vertices_pgr v
                        WHERE ST_DWithin(v.the_geom, ST_EndPoint(e.geom), 0);
                        """),
                new Step("compute travel-time cost", """
                        UPDATE car_edges_noded n SET
                            cost_s = CASE WHEN n.oneway = '-1' THEN -1 ELSE s.t END,
                            reverse_cost_s = CASE
                                WHEN n.oneway IN ('yes','true','1') OR n.junction = 'roundabout' THEN -1
                                ELSE s.t END
                        FROM (
                            SELECT id, ST_Length(geom::geography) / (%s / 3.6) AS t
                            FROM car_edges_noded
                        ) s WHERE n.id = s.id;
                        """.formatted(SPEED_KMH)),
                new Step("index topology", """
                        CREATE INDEX idx_car_edges_noded_source ON car_edges_noded(source);
                        CREATE INDEX idx_car_edges_noded_target ON car_edges_noded(target);
                        """));
    }
}
