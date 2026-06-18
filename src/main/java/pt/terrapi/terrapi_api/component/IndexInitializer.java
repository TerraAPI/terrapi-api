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
                    "CREATE INDEX IF NOT EXISTS idx_ap_geometry ON admin_unit_precisions USING GIST (geometry)");
        } catch (Exception e) {
            log.warn("Could not create GiST index (table may not exist yet): {}", e.getMessage());
        }
    }
}
