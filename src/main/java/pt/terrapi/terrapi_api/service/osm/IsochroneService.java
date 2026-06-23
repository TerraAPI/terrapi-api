package pt.terrapi.terrapi_api.service.osm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Computes car isochrones over the pgRouting car graph built by {@link TopologyService}.
 * Snaps the origin to the nearest graph vertex, runs {@code pgr_drivingDistance} within the time
 * budget, and returns the reachable area as a GeoJSON polygon (concave hull of reached vertices).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IsochroneService {

    private static final String SNAP_SQL = """
            SELECT id FROM car_edges_noded_vertices_pgr
            ORDER BY the_geom <-> ST_SetSRID(ST_MakePoint(?, ?), 4326)
            LIMIT 1
            """;

    /** Speed cap (km/h) used to size the search bbox so it never clips a reachable area. */
    private static final double CAP_SPEED_KMH = 120.0;
    private static final double SAFETY = 1.15;
    private static final double METERS_PER_DEGREE = 111_320.0;

    /**
     * The edge set fed to pgr_drivingDistance is bbox-restricted to a radius around the origin
     * (max reachable distance), so pgRouting builds a small local graph instead of loading the
     * whole-country 2.3M-edge graph on every call. The bbox filter uses the gist index on geom.
     */
    private static final String ISOCHRONE_SQL = """
            SELECT ST_AsGeoJSON(ST_ConcaveHull(ST_Collect(v.the_geom), 0.85))
            FROM car_edges_noded_vertices_pgr v
            JOIN pgr_drivingDistance(?, ?, ?, true) dd ON v.id = dd.node
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param lat     origin latitude (EPSG:4326)
     * @param lon     origin longitude (EPSG:4326)
     * @param minutes travel-time budget
     * @return GeoJSON geometry string of the reachable area, or {@code null} if nothing is reachable
     */
    public String computeGeoJson(double lat, double lon, double minutes) {
        long startVid = snapToVertex(lat, lon);
        double maxCostSeconds = minutes * 60.0;

        double radiusMeters = (CAP_SPEED_KMH / 3.6) * maxCostSeconds * SAFETY;
        double radiusDegrees = radiusMeters / METERS_PER_DEGREE;
        String edgesSql = ("SELECT id, source, target, cost_s AS cost, reverse_cost_s AS reverse_cost "
                + "FROM car_edges_noded "
                + "WHERE geom && ST_Expand(ST_SetSRID(ST_MakePoint(%s,%s),4326), %s)")
                .formatted(lon, lat, radiusDegrees);

        log.info("Isochrone: origin=({},{}) snapped to vertex {} budget={} min bbox~{} m",
                lat, lon, startVid, minutes, Math.round(radiusMeters));
        return jdbcTemplate.queryForObject(ISOCHRONE_SQL, String.class,
                edgesSql, startVid, maxCostSeconds);
    }

    private long snapToVertex(double lat, double lon) {
        try {
            Long id = jdbcTemplate.queryForObject(SNAP_SQL, Long.class, lon, lat);
            if (id == null) {
                throw new IllegalStateException("No routing vertex found near origin.");
            }
            return id;
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException(
                    "Car routing graph is empty — run an OSM import first.", e);
        }
    }
}
