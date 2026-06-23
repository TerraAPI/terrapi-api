package pt.terrapi.terrapi_api.service.osm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pt.terrapi.terrapi_api.config.OsmProperties;
import pt.terrapi.terrapi_api.dto.ImportResult;

/**
 * Imports the OSM highway network from an {@code .osm.pbf} file into the {@code routing_edges}
 * PostGIS table by shelling out to osm2pgsql (flex output). This is a rare, admin-only action:
 * it runs synchronously, fails loudly, and is kept gentle on the running server (small node cache
 * + flat-nodes) rather than optimised for speed. The {@code routing_edges} table is owned by
 * osm2pgsql; nothing in the app reads it yet.
 */
@Slf4j
@Service
public class OsmImportService {

    private static final int ERROR_TAIL_LINES = 30;

    private final JdbcTemplate jdbcTemplate;
    private final OsmProperties properties;
    private final String dbUri;
    private final String dbUser;
    private final String dbPassword;

    public OsmImportService(JdbcTemplate jdbcTemplate,
                            OsmProperties properties,
                            @Value("${spring.datasource.url}") String datasourceUrl,
                            @Value("${spring.datasource.username}") String dbUser,
                            @Value("${spring.datasource.password}") String dbPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.dbUri = datasourceUrl.replaceFirst("^jdbc:", "");
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public ImportResult importFolder(String folderPath) {
        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".pbf"));
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No .pbf files found in " + folderPath);
        }
        if (files.length > 1) {
            throw new IllegalArgumentException(
                    "Found " + files.length + " .pbf files in " + folderPath
                            + "; import one file at a time (osm2pgsql recreates routing_edges).");
        }
        return importPbf(files[0].getAbsolutePath());
    }

    public ImportResult importPbf(String filePath) {
        File pbf = new File(filePath);
        if (!pbf.isFile()) {
            throw new IllegalArgumentException("PBF file not found: " + filePath);
        }

        Path style = null;
        long t0 = System.currentTimeMillis();
        try {
            style = extractStyle();

            runOsm2pgsql(pbf.getAbsolutePath(), style);

            ImportResult result = countByHighway();
            if (result.total() == 0) {
                throw new IllegalStateException(
                        "osm2pgsql finished but routing_edges is empty — import failed.");
            }
            log.info("OSM import finished — {} edges in {} ms",
                    result.total(), System.currentTimeMillis() - t0);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("OSM import failed: " + e.getMessage(), e);
        } finally {
            deleteQuietly(style);
        }
    }

    private void runOsm2pgsql(String pbfPath, Path style) throws IOException {
        int processes = properties.getNumberProcesses() > 0
                ? properties.getNumberProcesses()
                : Runtime.getRuntime().availableProcessors();

        List<String> cmd = new ArrayList<>(List.of(
                properties.getOsm2pgsqlPath(),
                "--output=flex",
                "--style=" + style.toAbsolutePath(),
                "-d", dbUri));

        Path flatNodes = null;
        if (properties.isSlim()) {
            flatNodes = Files.createTempFile("osm2pgsql_nodes_", ".cache");
            Files.deleteIfExists(flatNodes);
            cmd.add("--slim");
            cmd.add("--drop");
            cmd.add("--flat-nodes");
            cmd.add(flatNodes.toAbsolutePath().toString());
            cmd.add("--cache");
            cmd.add("0");
        }
        // non-slim: middle is held in RAM; osm2pgsql rejects --cache outside slim mode.
        cmd.add("--number-processes");
        cmd.add(String.valueOf(processes));
        cmd.add("--log-progress=true");
        cmd.add(pbfPath);

        log.info("Running osm2pgsql ({} mode): {}",
                properties.isSlim() ? "slim" : "non-slim", String.join(" ", cmd));
        try {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("PGUSER", dbUser);
        pb.environment().put("PGPASSWORD", dbPassword);
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IOException(
                    "Could not start osm2pgsql ('" + properties.getOsm2pgsqlPath()
                            + "'). Is it installed and on PATH?", e);
        }

        Deque<String> tail = new ArrayDeque<>();
        long throttleMs = Math.max(0, properties.getLogProgressSeconds()) * 1000L;
        long lastProgressLog = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("[osm2pgsql] {}", line);
                tail.addLast(line);
                if (tail.size() > ERROR_TAIL_LINES) {
                    tail.removeFirst();
                }
                if (line.isBlank()) {
                    continue;
                }
                if (line.contains("Processing:")) {
                    long now = System.currentTimeMillis();
                    if (now - lastProgressLog >= throttleMs) {
                        log.info("[osm2pgsql] {}", line.trim());
                        lastProgressLog = now;
                    }
                } else {
                    log.info("[osm2pgsql] {}", line.trim());
                }
            }
        }

        int exit;
        try {
            exit = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("osm2pgsql was interrupted", e);
        }
        if (exit != 0) {
            throw new IllegalStateException(
                    "osm2pgsql exited with code " + exit + ":\n" + String.join("\n", tail));
        }
        } finally {
            deleteQuietly(flatNodes);
        }
    }

    private Path extractStyle() throws IOException {
        ClassPathResource resource = new ClassPathResource(properties.getStyleResource());
        if (!resource.exists()) {
            throw new IllegalStateException("Lua style not found on classpath: " + properties.getStyleResource());
        }
        Path temp = Files.createTempFile("routing_edges_", ".lua");
        try (var in = resource.getInputStream()) {
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return temp;
    }

    private ImportResult countByHighway() {
        Map<String, Integer> counts = new HashMap<>();
        jdbcTemplate.query("SELECT highway, count(*) AS n FROM routing_edges GROUP BY highway",
                rs -> {
                    counts.put(rs.getString("highway"), rs.getInt("n"));
                });
        return new ImportResult(counts);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete temp file {}: {}", path, e.getMessage());
        }
    }
}
