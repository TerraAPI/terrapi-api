package pt.terrapi.terrapi_api.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

/**
 * Builds whole-layer ("grid") GeoJSON FeatureCollections for a {@link GeoUnitType} at a given LOD,
 * optionally filtered to the children of a parent unit. Geometry is selection-grade simplified
 * (LOD &gt;= 1, served from {@code geo_unit_precisions}); LOD 0 returns full detail from
 * {@code geo_units}. The precise boundary of a selected unit is fetched separately via the
 * per-unit geometry endpoint.
 */
@Slf4j
@Service
public class LayerService {

    static final String EMPTY_FEATURE_COLLECTION =
            "{\"type\":\"FeatureCollection\",\"features\":[]}";

    private static final String FEATURES_FROM_PRECISIONS = """
            SELECT jsonb_build_object(
                'type', 'FeatureCollection',
                'features', COALESCE(jsonb_agg(jsonb_build_object(
                    'type', 'Feature',
                    'id', gp.geo_unit_code,
                    'properties', jsonb_build_object(
                        'code', gp.geo_unit_code,
                        'name', COALESCE(u.simplified_name, u.name)),
                    'geometry', ST_AsGeoJSON(ST_Transform(gp.geometry, 4326))::jsonb)), '[]'::jsonb))::text
            FROM geo_unit_precisions gp
            JOIN geo_units u ON u.code = gp.geo_unit_code
            WHERE gp.type = ? AND gp.lod = ? AND gp.status = 'ACTIVE'
            """;

    private static final String FEATURES_FROM_GEO_UNITS = """
            SELECT jsonb_build_object(
                'type', 'FeatureCollection',
                'features', COALESCE(jsonb_agg(jsonb_build_object(
                    'type', 'Feature',
                    'id', u.code,
                    'properties', jsonb_build_object(
                        'code', u.code,
                        'name', COALESCE(u.simplified_name, u.name)),
                    'geometry', ST_AsGeoJSON(u.geometry)::jsonb)), '[]'::jsonb))::text
            FROM geo_units u
            WHERE u.type = ?
              AND u.geometry IS NOT NULL
              AND NOT ST_IsEmpty(u.geometry)
            """;

    private static final String GENERATION_ID_SQL = """
            SELECT generation_id::text
            FROM geo_unit_precisions
            WHERE type = ? AND status = 'ACTIVE'
            ORDER BY created_at DESC
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private Cache<String, String> layerCache;

    public LayerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void initCache() {
        layerCache = Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterAccess(Duration.ofHours(12))
                .build();
    }

    public String renderLayer(GeoUnitType type, int lod, String parent) {
        String genId = getGenerationId(type);
        String cacheKey = genId + ":" + type.getValue() + ":" + lod + ":" + normalizeParent(parent);
        return layerCache.get(cacheKey, key -> queryLayer(type, lod, parent));
    }

    public String getETag(GeoUnitType type, int lod, String parent) {
        return type.name().toLowerCase() + "-" + lod + "-" + normalizeParent(parent)
                + "-" + getGenerationId(type);
    }

    private String queryLayer(GeoUnitType type, int lod, String parent) {
        boolean original = lod <= 0;
        String sql = buildLayerSql(original, hasParent(parent));
        List<Object> params = new ArrayList<>();
        params.add(type.getValue());
        if (!original) {
            params.add(lod);
        }
        if (hasParent(parent)) {
            params.add(parent);
        }
        String result = jdbcTemplate.queryForObject(sql, String.class, params.toArray());
        return result != null ? result : EMPTY_FEATURE_COLLECTION;
    }

    static String buildLayerSql(boolean original, boolean hasParent) {
        StringBuilder sql = new StringBuilder(original ? FEATURES_FROM_GEO_UNITS : FEATURES_FROM_PRECISIONS);
        if (hasParent) {
            sql.append("  AND u.parent_code = ?\n");
        }
        return sql.toString();
    }

    private String getGenerationId(GeoUnitType type) {
        List<String> ids = jdbcTemplate.query(GENERATION_ID_SQL,
                (rs, rowNum) -> rs.getString(1), type.getValue());
        return ids.isEmpty() || ids.get(0) == null ? "none" : ids.get(0);
    }

    private static boolean hasParent(String parent) {
        return parent != null && !parent.isBlank();
    }

    private static String normalizeParent(String parent) {
        return hasParent(parent) ? parent : "all";
    }
}
