package pt.terrapi.web.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Serves classified administrative boundary lines (CAOP {@code trocos}) as a GeoJSON
 * FeatureCollection, optionally filtered by border order (level). Geometry is always served from
 * {@code border_segment_precisions} (independently line-simplified per LOD, the same ladder as the
 * layers; LOD 0 = most detailed, 2 = coarsest). The full-detail {@code border_segments} are never
 * dumped wholesale - they exist only for derivation and spatial-correctness queries.
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
            FROM border_segment_precisions
            WHERE geometry IS NOT NULL AND lod = ?
            """;

    private static final String GENERATION_ID_SQL = """
            SELECT generation_id::text
            FROM border_segment_precisions
            ORDER BY created_at DESC
            LIMIT 1
            """;

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

    public String renderBorders(Integer maxLevel, int lod) {
        String key = getVersion() + ":" + lod + ":" + (maxLevel == null ? "all" : maxLevel);
        return cache.get(key, k -> query(maxLevel, lod));
    }

    public String getETag(Integer maxLevel, int lod) {
        return "borders-" + lod + "-" + (maxLevel == null ? "all" : maxLevel) + "-" + getVersion();
    }

    static String buildSql(boolean hasMaxLevel) {
        return hasMaxLevel ? FEATURES_SQL + "  AND level <= ?\n" : FEATURES_SQL;
    }

    private String query(Integer maxLevel, int lod) {
        boolean hasMaxLevel = maxLevel != null;
        List<Object> params = new ArrayList<>();
        params.add(lod);
        if (hasMaxLevel) {
            params.add(maxLevel);
        }
        String result = jdbcTemplate.queryForObject(buildSql(hasMaxLevel), String.class, params.toArray());
        return result != null ? result : EMPTY_FEATURE_COLLECTION;
    }

    private String getVersion() {
        List<String> ids = jdbcTemplate.query(GENERATION_ID_SQL, (rs, rowNum) -> rs.getString(1));
        return ids.isEmpty() || ids.get(0) == null ? "none" : ids.get(0);
    }
}
