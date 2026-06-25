package pt.terrapi.terrapi_api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the database objects that Hibernate {@code ddl-auto} and Flyway don't own: the
 * {@code geo_unit_adjacency} helper table (not a JPA entity) and the spatial/lookup indexes on
 * the geometry tables.
 *
 * <p>Runs as an {@link ApplicationRunner} — i.e. after Hibernate has created/updated the entity
 * tables. A Flyway migration can't create these indexes because Flyway runs before
 * {@code ddl-auto}. Every statement is idempotent and executed independently, so one failure
 * (e.g. on a non-PostGIS database) does not skip the rest.
 */
@Slf4j
@Component
public class SchemaInitializer implements ApplicationRunner {

    private static final String[] STATEMENTS = {
            // Helper table not mapped by a JPA entity (written by GeoDerivationService).
            """
            CREATE TABLE IF NOT EXISTS geo_unit_adjacency (
                code VARCHAR(6) NOT NULL,
                neighbour_code VARCHAR(6) NOT NULL,
                PRIMARY KEY (code, neighbour_code)
            )""",
            "CREATE INDEX IF NOT EXISTS idx_adjacency_code ON geo_unit_adjacency (code)",
            // Spatial + lookup indexes on the entity tables.
            "CREATE INDEX IF NOT EXISTS idx_geo_units_geometry ON geo_units USING GIST (geometry)",
            "CREATE INDEX IF NOT EXISTS idx_gp_geometry ON geo_unit_precisions USING GIST (geometry)",
            "CREATE INDEX IF NOT EXISTS idx_gp_type_lod ON geo_unit_precisions (type, lod)"
                    + " INCLUDE (geo_unit_code)",
            "CREATE INDEX IF NOT EXISTS idx_border_geometry ON border_segments USING GIST (geometry)",
            "CREATE INDEX IF NOT EXISTS idx_bsp_geometry"
                    + " ON border_segment_precisions USING GIST (geometry)",
            "DROP INDEX IF EXISTS idx_bsp_level_lod",
            "CREATE INDEX IF NOT EXISTS idx_bsp_lod_level ON border_segment_precisions (lod, level)"
                    + " INCLUDE (border_segment_id)",
    };

    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String sql : STATEMENTS) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.warn("Schema init statement failed (continuing): {} — {}",
                        sql.lines().findFirst().orElse(sql).trim(), e.getMessage());
            }
        }
    }
}
