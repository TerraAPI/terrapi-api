package pt.terrapi.pipeline.service.caop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Post-import, set-based derivations over the imported data: interior representative points,
 * the unit adjacency graph (from boundary arcs), and coastline lengths.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeoDerivationService {

    private final JdbcTemplate jdbcTemplate;

    public void deriveAll() {
        computeProjectedGeometry();
        updateRepresentativePoints();
        buildAdjacency();
        computeCoastline();
    }

    /**
     * Pre-transforms the 4326 source geometry to EPSG:3763 once per import, so precision
     * generation simplifies straight from {@code geometry_3763} without re-projecting every run.
     */
    void computeProjectedGeometry() {
        long t0 = System.currentTimeMillis();
        int units = jdbcTemplate.update(
                "UPDATE geo_units SET geometry_3763 = ST_Transform(geometry, 3763) "
                        + "WHERE geometry IS NOT NULL AND NOT ST_IsEmpty(geometry)");
        int borders = jdbcTemplate.update(
                "UPDATE border_segments SET geometry_3763 = ST_Transform(geometry, 3763) "
                        + "WHERE geometry IS NOT NULL AND NOT ST_IsEmpty(geometry)");
        log.info("  Projected to 3763: {} units, {} borders in {} ms",
                units, borders, System.currentTimeMillis() - t0);
    }

    void updateRepresentativePoints() {
        int updated = jdbcTemplate.update(
                "UPDATE geo_units SET representative_point = ST_PointOnSurface(geometry) "
                        + "WHERE geometry IS NOT NULL AND NOT ST_IsEmpty(geometry)");
        log.info("  Representative points computed for {} units", updated);
    }

    void buildAdjacency() {
        // Parish adjacency, directly from the boundary-arc side codes.
        jdbcTemplate.update("""
                WITH pairs AS (
                    SELECT DISTINCT ea_right AS a, ea_left AS b
                    FROM border_segments
                    WHERE ea_right ~ '^[0-9]{6}$' AND ea_left ~ '^[0-9]{6}$' AND ea_right <> ea_left
                )
                INSERT INTO geo_unit_adjacency (code, neighbour_code)
                SELECT a, b FROM pairs UNION SELECT b, a FROM pairs
                ON CONFLICT DO NOTHING
                """);
        // Municipality adjacency, aggregated from the parishes on each side.
        jdbcTemplate.update("""
                WITH pairs AS (
                    SELECT DISTINCT g1.parent_code AS a, g2.parent_code AS b
                    FROM border_segments s
                    JOIN geo_units g1 ON g1.code = s.ea_right
                    JOIN geo_units g2 ON g2.code = s.ea_left
                    WHERE s.ea_right ~ '^[0-9]{6}$' AND s.ea_left ~ '^[0-9]{6}$'
                      AND g1.parent_code IS NOT NULL AND g2.parent_code IS NOT NULL
                      AND g1.parent_code <> g2.parent_code
                )
                INSERT INTO geo_unit_adjacency (code, neighbour_code)
                SELECT a, b FROM pairs UNION SELECT b, a FROM pairs
                ON CONFLICT DO NOTHING
                """);
        // District/island adjacency, aggregated from the municipalities on each side.
        jdbcTemplate.update("""
                WITH pairs AS (
                    SELECT DISTINCT m1.parent_code AS a, m2.parent_code AS b
                    FROM border_segments s
                    JOIN geo_units g1 ON g1.code = s.ea_right
                    JOIN geo_units g2 ON g2.code = s.ea_left
                    JOIN geo_units m1 ON m1.code = g1.parent_code
                    JOIN geo_units m2 ON m2.code = g2.parent_code
                    WHERE s.ea_right ~ '^[0-9]{6}$' AND s.ea_left ~ '^[0-9]{6}$'
                      AND m1.parent_code IS NOT NULL AND m2.parent_code IS NOT NULL
                      AND m1.parent_code <> m2.parent_code
                )
                INSERT INTO geo_unit_adjacency (code, neighbour_code)
                SELECT a, b FROM pairs UNION SELECT b, a FROM pairs
                ON CONFLICT DO NOTHING
                """);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM geo_unit_adjacency", Long.class);
        log.info("  Adjacency pairs built: {}", count);
    }

    void computeCoastline() {
        // Parish coastline: sum of its coastal arcs.
        jdbcTemplate.update("""
                UPDATE geo_units g SET coastline_km = sub.km FROM (
                    SELECT code, SUM(length_km) AS km FROM (
                        SELECT ea_right AS code, length_km FROM border_segments
                         WHERE line_type = 'COAST' AND ea_right ~ '^[0-9]{6}$'
                        UNION ALL
                        SELECT ea_left AS code, length_km FROM border_segments
                         WHERE line_type = 'COAST' AND ea_left ~ '^[0-9]{6}$'
                    ) x GROUP BY code
                ) sub WHERE g.code = sub.code
                """);
        // Municipality coastline: sum over child parishes (type 3).
        jdbcTemplate.update("""
                UPDATE geo_units m SET coastline_km = sub.km FROM (
                    SELECT parent_code AS code, SUM(coastline_km) AS km
                    FROM geo_units
                    WHERE type = 3 AND coastline_km IS NOT NULL AND parent_code IS NOT NULL
                    GROUP BY parent_code
                ) sub WHERE m.code = sub.code
                """);
        // District/island coastline: sum over child municipalities (type 2).
        jdbcTemplate.update("""
                UPDATE geo_units d SET coastline_km = sub.km FROM (
                    SELECT parent_code AS code, SUM(coastline_km) AS km
                    FROM geo_units
                    WHERE type = 2 AND coastline_km IS NOT NULL AND parent_code IS NOT NULL
                    GROUP BY parent_code
                ) sub WHERE d.code = sub.code
                """);
        log.info("  Coastline lengths computed");
    }
}
