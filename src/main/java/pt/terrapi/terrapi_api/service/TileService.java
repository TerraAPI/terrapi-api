package pt.terrapi.terrapi_api.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

@Slf4j
@Service
public class TileService {

    private static final String TILE_SQL = """
            SELECT ST_AsMVT(features.*, 'default', 4096, 'geom')
            FROM (
                SELECT gp.geo_unit_code AS code,
                       ST_AsMVTGeom(gp.geometry, env, 4096, 64, true) AS geom
                FROM geo_unit_precisions gp
                CROSS JOIN LATERAL ST_TileEnvelope(?, ?, ?) env
                WHERE gp.type = ?
                  AND gp.lod = ?
                  AND gp.status = 'ACTIVE'
                  AND gp.geometry && env
            ) features
            """;

    private static final String ETAG_SQL = """
            SELECT generation_id::text
            FROM geo_unit_precisions
            WHERE type = ? AND lod = ? AND status = 'ACTIVE'
            ORDER BY created_at DESC
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private Cache<String, byte[]> tileCache;

    public TileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void initCache() {
        tileCache = Caffeine.newBuilder()
                .maximumSize(20000)
                .expireAfterAccess(Duration.ofHours(12))
                .build();
    }

    public byte[] renderTile(int z, int x, int y, GeoUnitType type, int lod) {
        String genId = getGenerationId(type, lod);
        String cacheKey = genId + ":" + z + ":" + x + ":" + y + ":" + type.getValue() + ":" + lod;
        return tileCache.get(cacheKey, key -> renderTileFromDB(z, x, y, type, lod));
    }

    public String getETag(GeoUnitType type, int lod) {
        String genId = getGenerationId(type, lod);
        return type.name().toLowerCase() + "-" + lod + "-" + genId;
    }

    private String getGenerationId(GeoUnitType type, int lod) {
        String genId = jdbcTemplate.queryForObject(ETAG_SQL,
                String.class, type.getValue(), lod);
        return genId != null ? genId : "none";
    }

    private byte[] renderTileFromDB(int z, int x, int y, GeoUnitType type, int lod) {
        byte[] tile = jdbcTemplate.queryForObject(TILE_SQL,
                byte[].class, z, x, y, type.getValue(), lod);
        return tile != null ? tile : new byte[0];
    }
}
