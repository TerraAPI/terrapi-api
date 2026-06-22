package pt.terrapi.terrapi_api.service.caop;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.io.WKBWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pt.terrapi.terrapi_api.entities.BorderSegment;
import pt.terrapi.terrapi_api.entities.GeoUnit;

/**
 * Writes CAOP entities into PostGIS: upserts geo units, inserts border segments, and clears the
 * derived auxiliary tables. Owns the batch SQL; geometries are transformed from their source SRID
 * to EPSG:4326 on insert.
 */
@Component
@RequiredArgsConstructor
public class GeoUnitWriter {

    private static final WKBWriter WKB_WRITER = new WKBWriter();

    private static final String UPSERT_GEO_UNIT_SQL = """
            INSERT INTO geo_units (code, name, geometry, area_ha, perimeter_km,
                                   type, parent_code, simplified_name, nuts3_code,
                                   municipality_count, parish_count)
            VALUES (?, ?, ST_Transform(ST_GeomFromWKB(?, ?), 4326),
                    ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (code) DO UPDATE SET
                name = EXCLUDED.name,
                geometry = EXCLUDED.geometry,
                area_ha = EXCLUDED.area_ha,
                perimeter_km = EXCLUDED.perimeter_km,
                type = EXCLUDED.type,
                parent_code = EXCLUDED.parent_code,
                simplified_name = EXCLUDED.simplified_name,
                nuts3_code = EXCLUDED.nuts3_code,
                municipality_count = EXCLUDED.municipality_count,
                parish_count = EXCLUDED.parish_count
            """;

    private static final String INSERT_BORDER_SQL = """
            INSERT INTO border_segments (geometry, level, line_type, ea_right, ea_left, length_km)
            VALUES (ST_Transform(ST_GeomFromWKB(?, ?), 4326), ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public void clearAuxData() {
        jdbcTemplate.update("DELETE FROM geo_unit_adjacency");
        jdbcTemplate.update("DELETE FROM border_segments");
    }

    public void upsertGeoUnits(List<GeoUnit> units, int sourceEpsg) {
        if (units.isEmpty()) return;
        jdbcTemplate.batchUpdate(UPSERT_GEO_UNIT_SQL, units, 50, (ps, u) -> {
            ps.setString(1, u.getCode());
            ps.setString(2, u.getName());
            setWkb(ps, 3, u.getGeometry() != null ? WKB_WRITER.write(u.getGeometry()) : null);
            ps.setInt(4, sourceEpsg);
            ps.setObject(5, u.getAreaHa());
            ps.setObject(6, u.getPerimeterKm());
            ps.setInt(7, u.getType().getValue());
            ps.setString(8, u.getParent() != null ? u.getParent().getCode() : null);
            ps.setString(9, u.getSimplifiedName());
            ps.setString(10, u.getNuts3Code());
            ps.setObject(11, u.getMunicipalityCount());
            ps.setObject(12, u.getParishCount());
        });
    }

    public void insertBorderSegments(List<BorderSegment> borders, int sourceEpsg) {
        if (borders.isEmpty()) return;
        jdbcTemplate.batchUpdate(INSERT_BORDER_SQL, borders, 100, (ps, b) -> {
            setWkb(ps, 1, b.getGeometry() != null ? WKB_WRITER.write(b.getGeometry()) : null);
            ps.setInt(2, sourceEpsg);
            ps.setObject(3, b.getLevel());
            ps.setString(4, b.getLineType());
            ps.setString(5, b.getEaRight());
            ps.setString(6, b.getEaLeft());
            ps.setObject(7, b.getLengthKm());
        });
    }

    private static void setWkb(java.sql.PreparedStatement ps, int index, byte[] wkb) throws java.sql.SQLException {
        if (wkb != null) {
            ps.setBytes(index, wkb);
        } else {
            ps.setNull(index, java.sql.Types.NULL);
        }
    }
}
