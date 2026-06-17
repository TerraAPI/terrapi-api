package pt.terrapi.terrapi_api.service;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.terrapi_api.dto.ImportResult;
import pt.terrapi.terrapi_api.entities.District;
import pt.terrapi.terrapi_api.entities.Municipality;
import pt.terrapi.terrapi_api.entities.Parish;
import pt.terrapi.terrapi_api.repository.DistrictRepository;
import pt.terrapi.terrapi_api.repository.MunicipalityRepository;
import pt.terrapi.terrapi_api.repository.ParishRepository;

@Service
public class CaopImportService {

    private final DistrictRepository districtRepository;
    private final MunicipalityRepository municipalityRepository;
    private final ParishRepository parishRepository;
    private final WKBReader wkbReader = new WKBReader();

    public CaopImportService(DistrictRepository districtRepository,
                             MunicipalityRepository municipalityRepository,
                             ParishRepository parishRepository) {
        this.districtRepository = districtRepository;
        this.municipalityRepository = municipalityRepository;
        this.parishRepository = parishRepository;
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

            Map<String, District> districtByName = importDistricts(conn, prefix);
            Map<String, Municipality> municipalityByName = importMunicipalities(conn, prefix, districtByName);
            int parishCount = importParishes(conn, prefix, municipalityByName);

            return new ImportResult(districtByName.size(), municipalityByName.size(), parishCount);
        } catch (Exception e) {
            throw new RuntimeException("Failed to import GPKG: " + e.getMessage(), e);
        }
    }

    private String detectPrefix(Connection conn) throws Exception {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name LIKE '%_distritos'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String table = rs.getString("name");
                return table.substring(0, table.length() - "distritos".length());
            }
        }
        return null;
    }

    private Map<String, District> importDistricts(Connection conn, String prefix) throws Exception {
        String sql = "SELECT dt, distrito, geom, area_ha, perimetro_km FROM " + prefix + "distritos";
        Map<String, District> map = new HashMap<>();
        List<District> batch = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                District d = new District();
                d.setCode(rs.getString("dt"));
                d.setName(rs.getString("distrito"));
                d.setPolygon(readGeometry(rs.getBytes("geom")));
                d.setAreaHa(rs.getDouble("area_ha"));
                d.setPerimeterKm(rs.getDouble("perimetro_km"));
                batch.add(d);
                map.put(d.getName(), d);
            }
        }

        districtRepository.saveAll(batch);
        return map;
    }

    private Map<String, Municipality> importMunicipalities(Connection conn, String prefix,
                                                            Map<String, District> districtByName) throws Exception {
        String sql = "SELECT dtmn, municipio, distrito_ilha, geom, area_ha, perimetro_km FROM " + prefix + "municipios";
        Map<String, Municipality> map = new HashMap<>();
        List<Municipality> batch = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String districtName = rs.getString("distrito_ilha");
                District district = districtByName.get(districtName);

                Municipality m = new Municipality();
                m.setCode(rs.getString("dtmn"));
                m.setName(rs.getString("municipio"));
                m.setDistrict(district);
                m.setPolygon(readGeometry(rs.getBytes("geom")));
                m.setAreaHa(rs.getDouble("area_ha"));
                m.setPerimeterKm(rs.getDouble("perimetro_km"));
                batch.add(m);
                map.put(m.getName(), m);
            }
        }

        municipalityRepository.saveAll(batch);
        return map;
    }

    private int importParishes(Connection conn, String prefix,
                                Map<String, Municipality> municipalityByName) throws Exception {
        String sql = "SELECT dtmnfr, freguesia, municipio, designacao_simplificada, geom, area_ha, perimetro_km FROM " + prefix + "freguesias";
        List<Parish> batch = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String municipalityName = rs.getString("municipio");
                Municipality municipality = municipalityByName.get(municipalityName);

                Parish p = new Parish();
                p.setCode(rs.getString("dtmnfr"));
                p.setName(rs.getString("freguesia"));
                p.setSimplifiedName(rs.getString("designacao_simplificada"));
                p.setMunicipality(municipality);
                p.setPolygon(readGeometry(rs.getBytes("geom")));
                p.setAreaHa(rs.getDouble("area_ha"));
                p.setPerimeterKm(rs.getDouble("perimetro_km"));
                batch.add(p);
            }
        }

        parishRepository.saveAll(batch);
        return batch.size();
    }

    private Geometry readGeometry(byte[] gpkgBlob) throws Exception {
        if (gpkgBlob == null) {
            return null;
        }
        byte[] wkb = stripGpkgHeader(gpkgBlob);
        return wkbReader.read(wkb);
    }

    private byte[] stripGpkgHeader(byte[] gpkgBlob) {
        // GeoPackage header: 4 bytes magic + 1 version + 1 flags + 4 srs_id = 8 bytes before WKB
        // For empty geometries, the flags byte indicates no geometry body
        int flags = gpkgBlob[3] & 0xFF;
        boolean empty = (flags & 0x10) != 0;
        if (empty) {
            return new byte[]{0x00, 0x00, 0x00, 0x00, 0x00}; // WKB empty geometry header
        }
        byte[] wkb = new byte[gpkgBlob.length - 8];
        System.arraycopy(gpkgBlob, 8, wkb, 0, wkb.length);
        return wkb;
    }
}
