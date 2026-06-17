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
        File file = new File(filePath);
        String url = "jdbc:sqlite:" + file.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(url)) {
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

    private String detectPrefix(Connection conn) throws Exception {
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

    private int importStatUnits(Connection conn, String prefix) throws Exception {
        int count = 0;

        Map<String, StatUnit> nuts1ByName = importNuts1(conn, prefix);
        count += nuts1ByName.size();

        Map<String, StatUnit> nuts2ByName = importNuts2(conn, prefix, nuts1ByName);
        count += nuts2ByName.size();

        count += importNuts3(conn, prefix, nuts2ByName);

        return count;
    }

    private Map<String, StatUnit> importNuts1(Connection conn, String prefix) throws Exception {
        String table = prefix + "nuts1";
        if (!tableExists(conn, table)) return Map.of();

        var map = new HashMap<String, StatUnit>();
        var batch = new ArrayList<StatUnit>();

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.StatUnitMapper.nuts1Columns()))) {
            while (rs.next()) {
                StatUnit s = RowMappers.StatUnitMapper.mapRowNuts1(rs);
                statUnitRepository.findByCode(s.getCode()).ifPresent(existing -> s.setId(existing.getId()));
                batch.add(s);
                map.put(s.getName(), s);
            }
        }
        statUnitRepository.saveAll(batch);
        return map;
    }

    private Map<String, StatUnit> importNuts2(Connection conn, String prefix,
                                               Map<String, StatUnit> nuts1ByName) throws Exception {
        String table = prefix + "nuts2";
        if (!tableExists(conn, table)) return Map.of();

        var map = new HashMap<String, StatUnit>();
        var batch = new ArrayList<StatUnit>();

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.StatUnitMapper.nuts2Columns()))) {
            while (rs.next()) {
                StatUnit s = RowMappers.StatUnitMapper.mapRowNuts2(rs, nuts1ByName);
                statUnitRepository.findByCode(s.getCode()).ifPresent(existing -> s.setId(existing.getId()));
                batch.add(s);
                map.put(s.getName(), s);
            }
        }
        statUnitRepository.saveAll(batch);
        return map;
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
                statUnitRepository.findByCode(s.getCode()).ifPresent(existing -> s.setId(existing.getId()));
                batch.add(s);
            }
        }
        statUnitRepository.saveAll(batch);
        return batch.size();
    }

    private int importAdminUnits(Connection conn, String prefix) throws Exception {
        int count = 0;

        Map<String, AdminUnit> districtByName = importDistricts(conn, prefix);
        count += districtByName.size();

        Map<String, AdminUnit> municipalityByName = importMunicipalities(conn, prefix, districtByName);
        count += municipalityByName.size();

        count += importParishes(conn, prefix, municipalityByName);

        return count;
    }

    private Map<String, AdminUnit> importDistricts(Connection conn, String prefix) throws Exception {
        String table = prefix + "distritos";
        var map = new HashMap<String, AdminUnit>();
        var batch = new ArrayList<AdminUnit>();

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.AdminUnitMapper.districtColumns()))) {
            while (rs.next()) {
                AdminUnit a = RowMappers.AdminUnitMapper.mapRowDistrict(rs, prefix);
                adminUnitRepository.findByCode(a.getCode()).ifPresent(existing -> a.setId(existing.getId()));
                batch.add(a);
                map.put(a.getName(), a);
            }
        }
        adminUnitRepository.saveAll(batch);
        return map;
    }

    private Map<String, AdminUnit> importMunicipalities(Connection conn, String prefix,
                                                         Map<String, AdminUnit> districtByName) throws Exception {
        String table = prefix + "municipios";
        var map = new HashMap<String, AdminUnit>();
        var batch = new ArrayList<AdminUnit>();

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.AdminUnitMapper.municipalityColumns()))) {
            while (rs.next()) {
                AdminUnit a = RowMappers.AdminUnitMapper.mapRowMunicipality(rs, districtByName);
                adminUnitRepository.findByCode(a.getCode()).ifPresent(existing -> a.setId(existing.getId()));
                batch.add(a);
                map.put(a.getName(), a);
            }
        }
        adminUnitRepository.saveAll(batch);
        return map;
    }

    private int importParishes(Connection conn, String prefix,
                                Map<String, AdminUnit> municipalityByName) throws Exception {
        String table = prefix + "freguesias";
        var batch = new ArrayList<AdminUnit>();

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, RowMappers.AdminUnitMapper.parishColumns()))) {
            while (rs.next()) {
                AdminUnit a = RowMappers.AdminUnitMapper.mapRowParish(rs, municipalityByName);
                adminUnitRepository.findByCode(a.getCode()).ifPresent(existing -> a.setId(existing.getId()));
                batch.add(a);
            }
        }
        adminUnitRepository.saveAll(batch);
        return batch.size();
    }

    private String selectSql(String table, String[] columns) {
        var sb = new StringBuilder("SELECT ");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(columns[i]);
        }
        sb.append(", geom, area_ha, perimetro_km");
        sb.append(" FROM ").append(table);
        return sb.toString();
    }

    private boolean tableExists(Connection conn, String table) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return rs.next();
        }
    }
}
