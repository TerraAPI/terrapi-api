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
import pt.terrapi.terrapi_api.entities.District;
import pt.terrapi.terrapi_api.entities.Municipality;
import pt.terrapi.terrapi_api.entities.Nuts1;
import pt.terrapi.terrapi_api.entities.Nuts2;
import pt.terrapi.terrapi_api.entities.Nuts3;
import pt.terrapi.terrapi_api.entities.Parish;
import pt.terrapi.terrapi_api.mappers.DistrictRowMapper;
import pt.terrapi.terrapi_api.mappers.MunicipalityRowMapper;
import pt.terrapi.terrapi_api.mappers.Nuts1RowMapper;
import pt.terrapi.terrapi_api.mappers.Nuts2RowMapper;
import pt.terrapi.terrapi_api.mappers.Nuts3RowMapper;
import pt.terrapi.terrapi_api.mappers.ParishRowMapper;
import pt.terrapi.terrapi_api.repository.DistrictRepository;
import pt.terrapi.terrapi_api.repository.MunicipalityRepository;
import pt.terrapi.terrapi_api.repository.Nuts1Repository;
import pt.terrapi.terrapi_api.repository.Nuts2Repository;
import pt.terrapi.terrapi_api.repository.Nuts3Repository;
import pt.terrapi.terrapi_api.repository.ParishRepository;

@Service
@RequiredArgsConstructor
public class CaopImportService {

    private final Nuts1Repository nuts1Repository;
    private final Nuts2Repository nuts2Repository;
    private final Nuts3Repository nuts3Repository;
    private final DistrictRepository districtRepository;
    private final MunicipalityRepository municipalityRepository;
    private final ParishRepository parishRepository;

    @Transactional
    public ImportResult importGpkg(String filePath) {
        File file = new File(filePath);
        String url = "jdbc:sqlite:" + file.getAbsolutePath();

        try (Connection conn = DriverManager.getConnection(url)) {
            String prefix = detectPrefix(conn);
            if (prefix == null) {
                throw new IllegalArgumentException("No administrative tables found with recognised prefix");
            }

            Map<String, Nuts1> nuts1ByName = importNuts1(conn, prefix);
            Map<String, Nuts2> nuts2ByName = importNuts2(conn, prefix, nuts1ByName);
            int nuts3Count = importNuts3(conn, prefix, nuts2ByName);
            Map<String, District> districtByName = importDistricts(conn, prefix);
            Map<String, Municipality> municipalityByName = importMunicipalities(conn, prefix, districtByName);
            int parishCount = importParishes(conn, prefix, municipalityByName);

            return new ImportResult(nuts1ByName.size(), nuts2ByName.size(), nuts3Count,
                    districtByName.size(), municipalityByName.size(), parishCount);
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

    private Map<String, Nuts1> importNuts1(Connection conn, String prefix) throws Exception {
        String table = prefix + "nuts1";
        if (!tableExists(conn, table)) return Map.of();

        var map = new HashMap<String, Nuts1>();
        var batch = new ArrayList<Nuts1>();

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, Nuts1RowMapper.columns()))) {
            while (rs.next()) {
                Nuts1 n = Nuts1RowMapper.mapRow(rs);
                batch.add(n);
                map.put(n.getName(), n);
            }
        }
        nuts1Repository.saveAll(batch);
        return map;
    }

    private Map<String, Nuts2> importNuts2(Connection conn, String prefix,
                                            Map<String, Nuts1> nuts1ByName) throws Exception {
        String table = prefix + "nuts2";
        if (!tableExists(conn, table)) return Map.of();

        var map = new HashMap<String, Nuts2>();
        var batch = new ArrayList<Nuts2>();

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, Nuts2RowMapper.columns()))) {
            while (rs.next()) {
                Nuts2 n = Nuts2RowMapper.mapRow(rs, nuts1ByName);
                batch.add(n);
                map.put(n.getName(), n);
            }
        }
        nuts2Repository.saveAll(batch);
        return map;
    }

    private int importNuts3(Connection conn, String prefix,
                             Map<String, Nuts2> nuts2ByName) throws Exception {
        String table = prefix + "nuts3";
        if (!tableExists(conn, table)) return 0;

        var batch = new ArrayList<Nuts3>();

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, Nuts3RowMapper.columns()))) {
            while (rs.next()) {
                batch.add(Nuts3RowMapper.mapRow(rs, nuts2ByName));
            }
        }
        nuts3Repository.saveAll(batch);
        return batch.size();
    }

    private Map<String, District> importDistricts(Connection conn, String prefix) throws Exception {
        String table = prefix + "distritos";
        var map = new HashMap<String, District>();
        var batch = new ArrayList<District>();

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, DistrictRowMapper.columns()))) {
            while (rs.next()) {
                District d = DistrictRowMapper.mapRow(rs);
                batch.add(d);
                map.put(d.getName(), d);
            }
        }
        districtRepository.saveAll(batch);
        return map;
    }

    private Map<String, Municipality> importMunicipalities(Connection conn, String prefix,
                                                            Map<String, District> districtByName) throws Exception {
        String table = prefix + "municipios";
        var map = new HashMap<String, Municipality>();
        var batch = new ArrayList<Municipality>();

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, MunicipalityRowMapper.columns()))) {
            while (rs.next()) {
                Municipality m = MunicipalityRowMapper.mapRow(rs, districtByName);
                batch.add(m);
                map.put(m.getName(), m);
            }
        }
        municipalityRepository.saveAll(batch);
        return map;
    }

    private int importParishes(Connection conn, String prefix,
                                Map<String, Municipality> municipalityByName) throws Exception {
        String table = prefix + "freguesias";
        var batch = new ArrayList<Parish>();

        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(selectSql(table, ParishRowMapper.columns()))) {
            while (rs.next()) {
                batch.add(ParishRowMapper.mapRow(rs, municipalityByName));
            }
        }
        parishRepository.saveAll(batch);
        return batch.size();
    }

    private String selectSql(String table, String[] columns) {
        var sb = new StringBuilder();
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
