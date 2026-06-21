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
                    "CREATE INDEX IF NOT EXISTS idx_gp_type_lod_status ON geo_unit_precisions (type, lod, status)");
        } catch (Exception e) {
            log.warn("Could not create index (table may not exist yet): {}", e.getMessage());
        }
    }
}
