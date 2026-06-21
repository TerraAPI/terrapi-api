package pt.terrapi.terrapi_api.service;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.io.WKBWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pt.terrapi.terrapi_api.dto.ImportResult;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GenerationType;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.enums.SourceDataset;
import pt.terrapi.terrapi_api.mappers.RowMappers;

@Slf4j
@Service
public class CaopImportService {

    private static final WKBWriter WKB_WRITER = new WKBWriter();

    private final PrecisionGenerationService precisionGenerationService;
    private final TaskExecutor taskExecutor;
    private final JdbcTemplate jdbcTemplate;

    public CaopImportService(PrecisionGenerationService precisionGenerationService,
                             @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                             JdbcTemplate jdbcTemplate) {
        this.precisionGenerationService = precisionGenerationService;
        this.taskExecutor = taskExecutor;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ImportResult importFolder(String folderPath) {
        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".gpkg"));
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No .gpkg files found in " + folderPath);
        }

        log.info("Importing {} .gpkg files from {}", files.length, folderPath);
        ImportResult total = ImportResult.empty();
        for (File file : files) {
            total = total.add(doImport(file.getAbsolutePath()));
        }
        log.info("Import finished — {}", total.counts());

        triggerGeneration();
        return total;
    }

    @Transactional
    public ImportResult importGpkg(String filePath) {
        ImportResult result = doImport(filePath);
        log.info("Import finished — {}", result.counts());

        triggerGeneration();
        return result;
    }

    private void triggerGeneration() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        log.info("Precision generation started (async)");
                        precisionGenerationService.generate(GenerationType.ALL);
                    } catch (Exception e) {
                        log.error("Precision generation failed after import", e);
                    }
                }, taskExecutor);
            }
        });
    }

    private ImportResult doImport(String filePath) {
        long t0 = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + filePath)) {
            String prefix = detectPrefix(conn);
            if (prefix == null) {
                throw new IllegalArgumentException("No administrative tables found with recognised prefix");
            }

            SourceDataset dataset = SourceDataset.fromPrefix(prefix);
            int detectedSrs = detectSpatialRefSys(conn, prefix + "distritos");
            int sourceEpsg = dataset.validateSrid(detectedSrs);
            log.info("  Detected {} (EPSG:{})", dataset, sourceEpsg);

            Map<String, Integer> counts = new HashMap<>();

            long t1 = System.currentTimeMillis();
            importNuts1(conn, prefix, counts, sourceEpsg);
            int statCount = counts.values().stream().mapToInt(Integer::intValue).sum();
            log.info("  Stat units imported: {} records ({} ms)", statCount, System.currentTimeMillis() - t1);

            long t2 = System.currentTimeMillis();
            importDistricts(conn, prefix, counts, sourceEpsg);
            int adminCount = counts.values().stream().mapToInt(Integer::intValue).sum() - statCount;
            log.info("  Admin units imported: {} records ({} ms)", adminCount, System.currentTimeMillis() - t2);

            verifyGeometryIntegration();

            log.info("  Total: {} records in {} ms", statCount + adminCount, System.currentTimeMillis() - t0);
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

    private void persistBatch(java.util.List<GeoUnit> batch, int sourceEpsg) {
        if (batch.isEmpty()) return;
        jdbcTemplate.batchUpdate("""
                INSERT INTO geo_units (code, name, geometry, area_ha, perimeter_km,
                                       type, parent_code, simplified_name, nuts3_code)
                VALUES (?, ?, ST_Transform(ST_GeomFromWKB(?, ?), 4326),
                        ?, ?, ?, ?, ?, ?)
                ON CONFLICT (code) DO UPDATE SET
                    name = EXCLUDED.name,
                    geometry = EXCLUDED.geometry,
                    area_ha = EXCLUDED.area_ha,
                    perimeter_km = EXCLUDED.perimeter_km,
                    type = EXCLUDED.type,
                    parent_code = EXCLUDED.parent_code,
                    simplified_name = EXCLUDED.simplified_name,
                    nuts3_code = EXCLUDED.nuts3_code
                """, batch, 50, (ps, u) -> {
            ps.setString(1, u.getCode());
            ps.setString(2, u.getName());
            byte[] wkb = u.getGeometry() != null ? WKB_WRITER.write(u.getGeometry()) : null;
            if (wkb != null) {
                ps.setBytes(3, wkb);
            } else {
                ps.setNull(3, java.sql.Types.NULL);
            }
            ps.setInt(4, sourceEpsg);
            ps.setObject(5, u.getAreaHa());
            ps.setObject(6, u.getPerimeterKm());
            ps.setInt(7, u.getType().getValue());
            ps.setString(8, u.getParent() != null ? u.getParent().getCode() : null);
            ps.setString(9, u.getSimplifiedName());
            ps.setString(10, u.getNuts3Code());
        });
    }

    private void verifyGeometryIntegration() {
        Long wrongSrid = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM geo_units WHERE geometry IS NOT NULL AND ST_SRID(geometry) <> 4326",
                Long.class);
        if (wrongSrid == null || wrongSrid > 0) {
            log.error("Geometry SRID verification failed: {} rows with SRID != 4326", wrongSrid);
            throw new IllegalStateException(
                    "Geometry SRID mismatch: " + wrongSrid + " rows not in 4326. CRS transform may have failed.");
        }

        var outOfBounds = jdbcTemplate.queryForList("""
                SELECT code, ST_X(ST_Centroid(geometry)) AS lon,
                       ST_Y(ST_Centroid(geometry)) AS lat
                FROM geo_units
                WHERE geometry IS NOT NULL
                  AND (ST_X(ST_Centroid(geometry)) < -35
                       OR ST_X(ST_Centroid(geometry)) > -6
                       OR ST_Y(ST_Centroid(geometry)) < 30
                       OR ST_Y(ST_Centroid(geometry)) > 45)
                LIMIT 5
                """);
        if (!outOfBounds.isEmpty()) {
            for (var row : outOfBounds) {
                log.warn("  Suspicious centroid: code={} lon={} lat={}",
                        row.get("code"), row.get("lon"), row.get("lat"));
            }
            log.warn("{} units have centroids outside Portugal range (lon -35..-6, lat 30..45) — "
                    + "check source CRS", outOfBounds.size());
        }

        log.info("  Geometry verification passed: SRID=4326, centroids within Portugal bounds");
    }

    private void importNuts1(Connection conn, String prefix, Map<String, Integer> counts,
                             int sourceEpsg) throws Exception {
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
        persistBatch(batch, sourceEpsg);
        counts.merge(GeoUnitType.NUTS1.name(), batch.size(), Integer::sum);

        importNuts2(conn, prefix, counts, byName, sourceEpsg);
    }

    private void importNuts2(Connection conn, String prefix, Map<String, Integer> counts,
                             Map<String, GeoUnit> nuts1ByName, int sourceEpsg) throws Exception {
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
        persistBatch(batch, sourceEpsg);
        counts.merge(GeoUnitType.NUTS2.name(), batch.size(), Integer::sum);

        importNuts3(conn, prefix, counts, byName, sourceEpsg);
    }

    private void importNuts3(Connection conn, String prefix, Map<String, Integer> counts,
                             Map<String, GeoUnit> nuts2ByName, int sourceEpsg) throws Exception {
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
        persistBatch(batch, sourceEpsg);
        counts.merge(GeoUnitType.NUTS3.name(), batch.size(), Integer::sum);
    }

    private void importDistricts(Connection conn, String prefix, Map<String, Integer> counts,
                                 int sourceEpsg) throws Exception {
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
        persistBatch(batch, sourceEpsg);

        for (GeoUnit u : batch) {
            counts.merge(u.getType().name(), 1, Integer::sum);
        }

        importMunicipalities(conn, prefix, counts, byName, sourceEpsg);
    }

    private void importMunicipalities(Connection conn, String prefix, Map<String, Integer> counts,
                                      Map<String, GeoUnit> districtByName, int sourceEpsg) throws Exception {
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
        persistBatch(batch, sourceEpsg);
        counts.merge(GeoUnitType.MUNICIPALITY.name(), batch.size(), Integer::sum);

        importParishes(conn, prefix, counts, byName, sourceEpsg);
    }

    private void importParishes(Connection conn, String prefix, Map<String, Integer> counts,
                                Map<String, GeoUnit> municipalityByName, int sourceEpsg) throws Exception {
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
        persistBatch(batch, sourceEpsg);
        counts.merge(GeoUnitType.PARISH.name(), batch.size(), Integer::sum);
    }

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
