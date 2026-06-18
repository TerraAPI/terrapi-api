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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.terrapi_api.dto.ImportResult;
import pt.terrapi.terrapi_api.entities.AdminUnit;
import pt.terrapi.terrapi_api.entities.StatUnit;
import pt.terrapi.terrapi_api.mappers.RowMappers;
import pt.terrapi.terrapi_api.repository.AdminUnitRepository;
import pt.terrapi.terrapi_api.repository.StatUnitRepository;

@Service
@RequiredArgsConstructor
public class CaopImportService {

    private final StatUnitRepository statUnitRepository;
    private final AdminUnitRepository adminUnitRepository;

    @Transactional
    public ImportResult importFolder(String folderPath) {
        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".gpkg"));
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No .gpkg files found in " + folderPath);
        }

        ImportResult total = new ImportResult(0, 0);
        for (File file : files) {
            total = total.add(importGpkg(file.getAbsolutePath()));
        }
        return total;
    }

    @Transactional
    public ImportResult importGpkg(String filePath) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + filePath)) {
            String prefix = detectPrefix(conn);
            if (prefix == null) {
                throw new IllegalArgumentException("No administrative tables found with recognised prefix");
            }

            int statCount = importStatUnits(conn, prefix);
            int adminCount = importAdminUnits(conn, prefix);

            return new ImportResult(adminCount, statCount);
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

    // --- Stat units ---

    private int importStatUnits(Connection conn, String prefix) throws Exception {
        var nuts1ByName = importNuts1(conn, prefix);
        var nuts2ByName = importNuts2(conn, prefix, nuts1ByName);
        int nuts3Count = importNuts3(conn, prefix, nuts2ByName);
        return nuts1ByName.size() + nuts2ByName.size() + nuts3Count;
    }

    private Map<String, StatUnit> importNuts1(Connection conn, String prefix) throws Exception {
        String table = prefix + "nuts1";
        if (!tableExists(conn, table)) return Map.of();
        var batch = new ArrayList<StatUnit>();
        var byName = new HashMap<String, StatUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.StatUnitMapper.nuts1Columns()))) {
            while (rs.next()) {
                StatUnit s = RowMappers.StatUnitMapper.mapRowNuts1(rs);
                batch.add(s);
                byName.put(s.getName(), s);
            }
        }
        statUnitRepository.saveAll(batch);
        statUnitRepository.flush();
        return byName;
    }

    private Map<String, StatUnit> importNuts2(Connection conn, String prefix,
                                               Map<String, StatUnit> nuts1ByName) throws Exception {
        String table = prefix + "nuts2";
        if (!tableExists(conn, table)) return Map.of();
        var batch = new ArrayList<StatUnit>();
        var byName = new HashMap<String, StatUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.StatUnitMapper.nuts2Columns()))) {
            while (rs.next()) {
                StatUnit s = RowMappers.StatUnitMapper.mapRowNuts2(rs, nuts1ByName);
                batch.add(s);
                byName.put(s.getName(), s);
            }
        }
        statUnitRepository.saveAll(batch);
        statUnitRepository.flush();
        return byName;
    }

    private int importNuts3(Connection conn, String prefix,
                             Map<String, StatUnit> nuts2ByName) throws Exception {
        String table = prefix + "nuts3";
        if (!tableExists(conn, table)) return 0;
        var batch = new ArrayList<StatUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.StatUnitMapper.nuts3Columns()))) {
            while (rs.next()) {
                StatUnit s = RowMappers.StatUnitMapper.mapRowNuts3(rs, nuts2ByName);
                batch.add(s);
            }
        }
        statUnitRepository.saveAll(batch);
        return batch.size();
    }

    // --- Admin units ---

    private int importAdminUnits(Connection conn, String prefix) throws Exception {
        var districtByName = importDistricts(conn, prefix);
        var municipalityByName = importMunicipalities(conn, prefix, districtByName);
        int parishCount = importParishes(conn, prefix, municipalityByName);
        return districtByName.size() + municipalityByName.size() + parishCount;
    }

    private Map<String, AdminUnit> importDistricts(Connection conn, String prefix) throws Exception {
        String table = prefix + "distritos";
        var batch = new ArrayList<AdminUnit>();
        var byName = new HashMap<String, AdminUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.AdminUnitMapper.districtColumns()))) {
            while (rs.next()) {
                AdminUnit a = RowMappers.AdminUnitMapper.mapRowDistrict(rs, prefix);
                batch.add(a);
                byName.put(a.getName(), a);
            }
        }
        adminUnitRepository.saveAll(batch);
        adminUnitRepository.flush();
        return byName;
    }

    private Map<String, AdminUnit> importMunicipalities(Connection conn, String prefix,
                                                         Map<String, AdminUnit> districtByName) throws Exception {
        String table = prefix + "municipios";
        var batch = new ArrayList<AdminUnit>();
        var byName = new HashMap<String, AdminUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.AdminUnitMapper.municipalityColumns()))) {
            while (rs.next()) {
                AdminUnit a = RowMappers.AdminUnitMapper.mapRowMunicipality(rs, districtByName);
                batch.add(a);
                byName.put(a.getName(), a);
            }
        }
        adminUnitRepository.saveAll(batch);
        adminUnitRepository.flush();
        return byName;
    }

    private int importParishes(Connection conn, String prefix,
                                Map<String, AdminUnit> municipalityByName) throws Exception {
        String table = prefix + "freguesias";
        if (!tableExists(conn, table)) return 0;
        var batch = new ArrayList<AdminUnit>();
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.AdminUnitMapper.parishColumns()))) {
            while (rs.next()) {
                AdminUnit a = RowMappers.AdminUnitMapper.mapRowParish(rs, municipalityByName);
                batch.add(a);
            }
        }
        adminUnitRepository.saveAll(batch);
        return batch.size();
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
