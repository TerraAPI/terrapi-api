package pt.terrapi.terrapi_api.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the GiST spatial index on {@code geo_units.geometry} after Hibernate has
 * created/updated the schema. A Flyway migration cannot do this because Flyway runs before
 * {@code hibernate.ddl-auto} creates the table. The statement is idempotent.
 */
@Component
public class SpatialIndexInitializer implements ApplicationRunner {

    private static final String CREATE_GEOMETRY_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_geo_units_geometry ON geo_units USING GIST (geometry)";

    private final JdbcTemplate jdbcTemplate;

    public SpatialIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(CREATE_GEOMETRY_INDEX);
    }
}
