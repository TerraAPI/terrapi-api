package pt.terrapi.terrapi_api.service;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

@Slf4j
@Service
@RequiredArgsConstructor
public class TileService {

    private static final String TILE_SQL = """
            WITH bounds(env) AS (
                SELECT ST_TileEnvelope(?, ?, ?)
            )
            SELECT ST_AsMVT(features.*, 'default', 4096, 'geom')
            FROM (
                SELECT gp.geo_unit_code AS code,
                       gu.name,
                       ST_AsMVTGeom(gp.geometry, bounds.env, 4096, 64, true) AS geom
                FROM geo_unit_precisions gp
                JOIN geo_units gu ON gu.code = gp.geo_unit_code
                CROSS JOIN bounds
                WHERE gp.type = ?
                  AND gp.lod = ?
                  AND gp.status = 'ACTIVE'
                  AND gp.geometry && bounds.env
            ) features
            """;

    private static final String ETAG_SQL = """
            SELECT MAX(created_at)::text
            FROM geo_unit_precisions
            WHERE type = ? AND lod = ? AND status = 'ACTIVE'
            """;

    private final JdbcTemplate jdbcTemplate;

    public byte[] renderTile(int z, int x, int y, GeoUnitType type, int lod) {
        byte[] tile = jdbcTemplate.queryForObject(TILE_SQL,
                byte[].class, z, x, y, type.getValue(), lod);
        if (tile == null) {
            return new byte[0];
        }
        return tile;
    }

    public String getETag(GeoUnitType type, int lod) {
        String ts = jdbcTemplate.queryForObject(ETAG_SQL,
                String.class, type.getValue(), lod);
        if (ts == null) {
            return "empty";
        }
        return type.name().toLowerCase() + "-" + lod + "-" + ts;
    }
}
