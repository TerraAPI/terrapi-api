package pt.terrapi.pipeline.service.caop;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.io.WKBWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pt.terrapi.core.entities.BorderSegment;
import pt.terrapi.core.entities.GeoUnit;

/**
 * Writes CAOP entities into PostGIS: upserts geo units, inserts border segments, and clears the
 * derived auxiliary tables. Owns the batch SQL; geometries are transformed from their source SRID
 * to EPSG:4326 on insert.
 */
@Component
@RequiredArgsConstructor
public class GeoUnitWriter {

    private static final WKBWriter WKB_WRITER = new WKBWriter();

    /**
     * Upsert used by the full rebuild: a conflicting code <em>unions</em> the geometries and
     * <em>sums</em> the disjoint scalar attributes, so an entity split across files - the Azores
     * NUTS levels, whose {@code codigo} appears (partially) in both the Western and the
     * Central+Eastern GeoPackages - is reassembled into one complete unit instead of one half
     * silently overwriting the other. Idempotent for geometry ({@code ST_Union} of a fragment
     * already contained is a no-op); the rebuild starts from an empty table
     * ({@link #clearGeoUnits()}), so the scalar sums never double-count.
     */
    private static final String UPSERT_GEO_UNIT_SQL = """
            INSERT INTO geo_units (code, name, geometry, area_ha, perimeter_km,
                                   type, parent_code, simplified_name, nuts3_code,
                                   municipality_count, parish_count)
            VALUES (?, ?, ST_Transform(ST_GeomFromWKB(?, ?), 4326),
                    ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (code) DO UPDATE SET
                name = EXCLUDED.name,
                geometry = CASE
                    WHEN geo_units.geometry IS NULL THEN EXCLUDED.geometry
                    WHEN EXCLUDED.geometry IS NULL THEN geo_units.geometry
                    ELSE ST_Multi(ST_UnaryUnion(
                            ST_Collect(geo_units.geometry, EXCLUDED.geometry)))
                END,
                area_ha = COALESCE(geo_units.area_ha, 0) + COALESCE(EXCLUDED.area_ha, 0),
                perimeter_km = COALESCE(geo_units.perimeter_km, 0)
                               + COALESCE(EXCLUDED.perimeter_km, 0),
                type = EXCLUDED.type,
                parent_code = EXCLUDED.parent_code,
                simplified_name = EXCLUDED.simplified_name,
                nuts3_code = EXCLUDED.nuts3_code,
                municipality_count = COALESCE(geo_units.municipality_count, 0)
                                     + COALESCE(EXCLUDED.municipality_count, 0),
                parish_count = COALESCE(geo_units.parish_count, 0)
                               + COALESCE(EXCLUDED.parish_count, 0)
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

    /**
     * Empties {@code geo_units} ahead of a full rebuild. Safe to call: no other table holds a
     * foreign key to it - {@code geo_unit_precisions} and {@code geo_unit_adjacency} store the
     * code as a plain column and are regenerated after the import. Clearing first lets the
     * merge-mode upsert sum attributes without double-counting across re-imports.
     */
    public void clearGeoUnits() {
        jdbcTemplate.update("DELETE FROM geo_units");
    }

    /**
     * Upserts units transformed from {@code sourceEpsg} to EPSG:4326. Conflicting codes union
     * geometry and sum scalars (see {@link #UPSERT_GEO_UNIT_SQL}), so entities split across files
     * (the Azores NUTS) are reassembled; the rebuild clears the table first so sums don't repeat.
     */
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
