package pt.terrapi.terrapi_api.component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_gp_geometry ON geo_unit_precisions USING GIST (geometry)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_gp_type_lod"
                            + " ON geo_unit_precisions (type, lod)"
                            + " INCLUDE (geo_unit_code)");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_border_geometry"
                            + " ON border_segments USING GIST (geometry)");
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS geo_unit_adjacency (
                        code VARCHAR(6) NOT NULL,
                        neighbour_code VARCHAR(6) NOT NULL,
                        PRIMARY KEY (code, neighbour_code)
                    )""");
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_adjacency_code ON geo_unit_adjacency (code)");
        } catch (Exception e) {
            log.warn("Could not create index (table may not exist yet): {}", e.getMessage());
        }
    }
}
