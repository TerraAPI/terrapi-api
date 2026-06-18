package pt.terrapi.terrapi_api.service;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.terrapi_api.dto.ImportResult;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GenerationType;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.mappers.RowMappers;
import pt.terrapi.terrapi_api.repository.GeoUnitRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaopImportService {

    private final GeoUnitRepository geoUnitRepository;
    private final PrecisionGenerationService precisionGenerationService;

    @Transactional
    public ImportResult importFolder(String folderPath) {
        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".gpkg"));
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No .gpkg files found in " + folderPath);
        }

        ImportResult total = ImportResult.empty();
        for (File file : files) {
            total = total.add(doImport(file.getAbsolutePath()));
        }
        triggerGeneration();
        return total;
    }

    @Transactional
    public ImportResult importGpkg(String filePath) {
        ImportResult result = doImport(filePath);
        triggerGeneration();
        return result;
    }

    private void triggerGeneration() {
        try {
            geoUnitRepository.flush();
            precisionGenerationService.generate(GenerationType.ALL);
        } catch (Exception e) {
            log.error("Precision generation failed after import", e);
        }
    }

    private ImportResult doImport(String filePath) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + filePath)) {
            String prefix = detectPrefix(conn);
            if (prefix == null) {
                throw new IllegalArgumentException("No administrative tables found with recognised prefix");
            }

            Map<String, Integer> counts = new HashMap<>();

            importNuts1(conn, prefix, counts);
            importDistricts(conn, prefix, counts);

            return new ImportResult(counts);
        } catch (Exception e) {
            throw new RuntimeException("Failed to import GPKG: " + e.getMessage(), e);
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

    // --- Stat units (NUTS) ---

    private void importNuts1(Connection conn, String prefix, Map<String, Integer> counts) throws Exception {
        String table = prefix + "nuts1";
        if (!tableExists(conn, table)) return;
        var batch = new ArrayList<GeoUnit>();
        var byName = new HashMap<String, GeoUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.GeoUnitMapper.nuts1Columns()))) {
            while (rs.next()) {
                GeoUnit u = RowMappers.GeoUnitMapper.mapRowNuts1(rs);
                batch.add(u);
                byName.put(u.getName(), u);
            }
        }
        geoUnitRepository.saveAll(batch);
        geoUnitRepository.flush();
        counts.merge(GeoUnitType.NUTS1.name(), batch.size(), Integer::sum);

        // chain to children
        importNuts2(conn, prefix, counts, byName);
    }

    private void importNuts2(Connection conn, String prefix, Map<String, Integer> counts,
                             Map<String, GeoUnit> nuts1ByName) throws Exception {
        String table = prefix + "nuts2";
        if (!tableExists(conn, table)) return;
        var batch = new ArrayList<GeoUnit>();
        var byName = new HashMap<String, GeoUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.GeoUnitMapper.nuts2Columns()))) {
            while (rs.next()) {
                GeoUnit u = RowMappers.GeoUnitMapper.mapRowNuts2(rs, nuts1ByName);
                batch.add(u);
                byName.put(u.getName(), u);
            }
        }
        geoUnitRepository.saveAll(batch);
        geoUnitRepository.flush();
        counts.merge(GeoUnitType.NUTS2.name(), batch.size(), Integer::sum);

        importNuts3(conn, prefix, counts, byName);
    }

    private void importNuts3(Connection conn, String prefix, Map<String, Integer> counts,
                             Map<String, GeoUnit> nuts2ByName) throws Exception {
        String table = prefix + "nuts3";
        if (!tableExists(conn, table)) return;
        var batch = new ArrayList<GeoUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.GeoUnitMapper.nuts3Columns()))) {
            while (rs.next()) {
                GeoUnit u = RowMappers.GeoUnitMapper.mapRowNuts3(rs, nuts2ByName);
                batch.add(u);
            }
        }
        geoUnitRepository.saveAll(batch);
        counts.merge(GeoUnitType.NUTS3.name(), batch.size(), Integer::sum);
    }

    // --- Admin units ---

    private void importDistricts(Connection conn, String prefix, Map<String, Integer> counts) throws Exception {
        String table = prefix + "distritos";
        var batch = new ArrayList<GeoUnit>();
        var byName = new HashMap<String, GeoUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.GeoUnitMapper.districtColumns()))) {
            while (rs.next()) {
                GeoUnit u = RowMappers.GeoUnitMapper.mapRowDistrict(rs, prefix);
                batch.add(u);
                byName.put(u.getName(), u);
            }
        }
        geoUnitRepository.saveAll(batch);
        geoUnitRepository.flush();

        for (GeoUnit u : batch) {
            counts.merge(u.getType().name(), 1, Integer::sum);
        }

        importMunicipalities(conn, prefix, counts, byName);
    }

    private void importMunicipalities(Connection conn, String prefix, Map<String, Integer> counts,
                                      Map<String, GeoUnit> districtByName) throws Exception {
        String table = prefix + "municipios";
        var batch = new ArrayList<GeoUnit>();
        var byName = new HashMap<String, GeoUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.GeoUnitMapper.municipalityColumns()))) {
            while (rs.next()) {
                GeoUnit u = RowMappers.GeoUnitMapper.mapRowMunicipality(rs, districtByName);
                batch.add(u);
                byName.put(u.getName(), u);
            }
        }
        geoUnitRepository.saveAll(batch);
        geoUnitRepository.flush();
        counts.merge(GeoUnitType.MUNICIPALITY.name(), batch.size(), Integer::sum);

        importParishes(conn, prefix, counts, byName);
    }

    private void importParishes(Connection conn, String prefix, Map<String, Integer> counts,
                                Map<String, GeoUnit> municipalityByName) throws Exception {
        String table = prefix + "freguesias";
        if (!tableExists(conn, table)) return;
        var batch = new ArrayList<GeoUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.GeoUnitMapper.parishColumns()))) {
            while (rs.next()) {
                GeoUnit u = RowMappers.GeoUnitMapper.mapRowParish(rs, municipalityByName);
                batch.add(u);
            }
        }
        geoUnitRepository.saveAll(batch);
        counts.merge(GeoUnitType.PARISH.name(), batch.size(), Integer::sum);
    }

    // --- Helpers ---

    private static String selectSql(String table, String[] columns) {
        return "SELECT " + String.join(", ", columns) + ", geom, area_ha, perimetro_km FROM " + table;
    }

    private static boolean tableExists(Connection conn, String table) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return rs.next();
        }
    }
}
