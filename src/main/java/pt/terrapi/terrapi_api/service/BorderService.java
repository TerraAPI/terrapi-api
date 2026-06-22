package pt.terrapi.terrapi_api.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Serves classified administrative boundary lines (CAOP {@code trocos}) as a GeoJSON
 * FeatureCollection, optionally filtered by border order (level).
 */
@Slf4j
@Service
public class BorderService {

    static final String EMPTY_FEATURE_COLLECTION =
            "{\"type\":\"FeatureCollection\",\"features\":[]}";

    private static final String FEATURES_SQL = """
            SELECT jsonb_build_object(
                'type', 'FeatureCollection',
                'features', COALESCE(jsonb_agg(jsonb_build_object(
                    'type', 'Feature',
                    'properties', jsonb_build_object(
                        'level', level, 'type', line_type, 'lengthKm', length_km),
                    'geometry', ST_AsGeoJSON(geometry)::jsonb)), '[]'::jsonb))::text
            FROM border_segments
            WHERE geometry IS NOT NULL
            """;

    private static final String VERSION_SQL =
            "SELECT COALESCE(MAX(id), 0)::text FROM border_segments";

    private final JdbcTemplate jdbcTemplate;
    private Cache<String, String> cache;

    public BorderService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void initCache() {
        cache = Caffeine.newBuilder()
                .maximumSize(50)
                .expireAfterAccess(Duration.ofHours(12))
                .build();
    }

    public String renderBorders(Integer maxLevel) {
        String key = getVersion() + ":" + (maxLevel == null ? "all" : maxLevel);
        return cache.get(key, k -> query(maxLevel));
    }

    public String getETag(Integer maxLevel) {
        return "borders-" + (maxLevel == null ? "all" : maxLevel) + "-" + getVersion();
    }

    static String buildSql(boolean hasMaxLevel) {
        return hasMaxLevel ? FEATURES_SQL + "  AND level <= ?\n" : FEATURES_SQL;
    }

    private String query(Integer maxLevel) {
        String result = maxLevel == null
                ? jdbcTemplate.queryForObject(buildSql(false), String.class)
                : jdbcTemplate.queryForObject(buildSql(true), String.class, maxLevel);
        return result != null ? result : EMPTY_FEATURE_COLLECTION;
    }

    private String getVersion() {
        String version = jdbcTemplate.queryForObject(VERSION_SQL, String.class);
        return version != null ? version : "0";
    }
}
