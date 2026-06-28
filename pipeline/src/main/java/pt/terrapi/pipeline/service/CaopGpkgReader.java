package pt.terrapi.pipeline.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pt.terrapi.core.entities.BorderSegment;
import pt.terrapi.core.entities.GeoUnit;
import pt.terrapi.core.enums.SourceDataset;
import pt.terrapi.core.mappers.RowMappers;

/**
 * Reads a CAOP GeoPackage (SQLite) file into flat, parent-linked entity lists. Owns all
 * source-side concerns (prefix/SRS detection, table access, row mapping); knows nothing about
 * the target database.
 */
@Slf4j
@Component
public class CaopGpkgReader {

    /** Source geometry + classification read from one GeoPackage, in its native SRID. */
    public record GpkgData(int sourceEpsg, List<GeoUnit> units, List<BorderSegment> borders) {}

    public GpkgData read(String filePath) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + filePath)) {
            String prefix = detectPrefix(conn);
            if (prefix == null) {
                throw new IllegalArgumentException("No administrative tables found with recognised prefix");
            }
            SourceDataset dataset = SourceDataset.fromPrefix(prefix);
            int sourceEpsg = dataset.validateSrid(detectSpatialRefSys(conn, prefix + "distritos"));
            log.info("  Detected {} (EPSG:{})", dataset, sourceEpsg);

            List<GeoUnit> units = new ArrayList<>();
            readStatistical(conn, prefix, units);
            readAdministrative(conn, prefix, units);
            resolveParentsByCode(units);
            List<BorderSegment> borders = readBorders(conn, prefix);

            return new GpkgData(sourceEpsg, units, borders);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read GPKG: " + e.getMessage(), e);
        }
    }

    private void readStatistical(Connection conn, String prefix, List<GeoUnit> out) throws Exception {
        Map<String, GeoUnit> nuts1 = readLevel(conn, prefix + "nuts1",
                RowMappers.GeoUnitMapper.nuts1Columns(), out,
                rs -> RowMappers.GeoUnitMapper.mapRowNuts1(rs));
        Map<String, GeoUnit> nuts2 = readLevel(conn, prefix + "nuts2",
                RowMappers.GeoUnitMapper.nuts2Columns(), out,
                rs -> RowMappers.GeoUnitMapper.mapRowNuts2(rs, nuts1));
        readLevel(conn, prefix + "nuts3",
                RowMappers.GeoUnitMapper.nuts3Columns(), out,
                rs -> RowMappers.GeoUnitMapper.mapRowNuts3(rs, nuts2));
    }

    private void readAdministrative(Connection conn, String prefix, List<GeoUnit> out) throws Exception {
        Map<String, GeoUnit> districts = readLevel(conn, prefix + "distritos",
                RowMappers.GeoUnitMapper.districtColumns(), out,
                rs -> RowMappers.GeoUnitMapper.mapRowDistrict(rs, prefix));
        Map<String, GeoUnit> municipalities = readLevel(conn, prefix + "municipios",
                RowMappers.GeoUnitMapper.municipalityColumns(), out,
                rs -> RowMappers.GeoUnitMapper.mapRowMunicipality(rs, districts));
        readLevel(conn, prefix + "freguesias",
                RowMappers.GeoUnitMapper.parishColumns(), out,
                rs -> RowMappers.GeoUnitMapper.mapRowParish(rs, municipalities));
    }

    private void resolveParentsByCode(List<GeoUnit> units) {
        Map<String, GeoUnit> byCode = new HashMap<>();
        for (GeoUnit u : units) {
            byCode.put(u.getCode(), u);
        }
        for (GeoUnit u : units) {
            if (u.getParent() != null || u.getCode() == null) {
                continue;
            }
            GeoUnit resolved = null;
            switch (u.getType()) {
                case MUNICIPALITY -> {
                    String districtCode = u.getCode().substring(0, 2);
                    resolved = byCode.get(districtCode);
                }
                case PARISH -> {
                    String muniCode = u.getCode().substring(0, 4);
                    resolved = byCode.get(muniCode);
                }
                case NUTS2 -> {
                    if (u.getCode().length() >= 3) {
                        resolved = byCode.get(u.getCode().substring(0, 3));
                    }
                }
                case NUTS3 -> {
                    if (u.getCode().length() >= 4) {
                        resolved = byCode.get(u.getCode().substring(0, 4));
                    }
                }
            }
            if (resolved != null) {
                log.warn("  Parent resolved by code-prefix fallback: {} ({}) -> {} ({})",
                        u.getCode(), u.getName(), resolved.getCode(), resolved.getName());
                u.setParent(resolved);
            }
        }
    }

    /**
     * Reads one administrative level, appends the units to {@code out}, and returns a name→unit
     * map so the next (child) level can resolve its parent.
     */
    private Map<String, GeoUnit> readLevel(Connection conn, String table, String[] columns,
                                           List<GeoUnit> out, RowFactory factory) throws Exception {
        Map<String, GeoUnit> byName = new HashMap<>();
        if (!tableExists(conn, table)) {
            return byName;
        }
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql(table, columns))) {
            while (rs.next()) {
                GeoUnit unit = factory.map(rs);
                out.add(unit);
                byName.put(unit.getName(), unit);
            }
        }
        return byName;
    }

    private List<BorderSegment> readBorders(Connection conn, String prefix) throws Exception {
        String table = prefix + "trocos";
        List<BorderSegment> borders = new ArrayList<>();
        if (!tableExists(conn, table)) {
            return borders;
        }
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT geom, ea_direita, ea_esquerda, "
                     + "nivel_limite_admin, significado_linha, comprimento_km FROM " + table)) {
            while (rs.next()) {
                borders.add(RowMappers.mapBorderSegment(rs));
            }
        }
        warnOnSuspectBorderClassification(table, borders);
        return borders;
    }

    /**
     * Border level ({@code nivel_limite_admin}) and line type ({@code significado_linha}) are
     * parsed with text heuristics in {@link RowMappers}; a change in the source wording would make
     * them silently mis-classify. Warn loudly on the tell-tale signs: any unparseable level, or a
     * dataset that produced zero COAST/WATER arcs (every CAOP region touches the sea).
     */
    private static void warnOnSuspectBorderClassification(String table, List<BorderSegment> borders) {
        if (borders.isEmpty()) {
            return;
        }
        long unparseableLevel = borders.stream().filter(b -> b.getLevel() == null).count();
        long coastalOrWater = borders.stream()
                .filter(b -> "COAST".equals(b.getLineType()) || "WATER".equals(b.getLineType()))
                .count();
        if (unparseableLevel > 0) {
            log.warn("{}: {}/{} border arcs have an unparseable admin level - check "
                    + "nivel_limite_admin wording", table, unparseableLevel, borders.size());
        }
        if (coastalOrWater == 0) {
            log.warn("{}: 0 of {} border arcs classified as COAST/WATER - significado_linha wording "
                    + "may have changed and parseLineType silently defaulted everything to LAND",
                    table, borders.size());
        }
    }

    private static String detectPrefix(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE '%_distritos'")) {
            if (rs.next()) {
                String name = rs.getString("name");
                return name.substring(0, name.length() - "distritos".length());
            }
        }
        return null;
    }

    private static int detectSpatialRefSys(Connection conn, String table) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT srs_id FROM gpkg_geometry_columns WHERE table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("srs_id");
                }
            }
        }
        throw new IllegalArgumentException("No SRS found for table: " + table);
    }

    private static boolean tableExists(Connection conn, String table) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return rs.next();
        }
    }

    private static String selectSql(String table, String[] columns) {
        return "SELECT " + String.join(", ", columns) + ", geom, area_ha, perimetro_km FROM " + table;
    }

    @FunctionalInterface
    private interface RowFactory {
        GeoUnit map(ResultSet rs) throws Exception;
    }
}
