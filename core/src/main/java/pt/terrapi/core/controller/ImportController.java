package pt.terrapi.core.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pt.terrapi.terrapi_api.dto.ImportResult;
import pt.terrapi.terrapi_api.service.caop.CaopImportService;
import pt.terrapi.terrapi_api.service.osm.OsmImportService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@RestController
@RequestMapping("/api/v1/import")
@Tag(name = "Import", description = "Import CAOP (GeoPackage) and OSM (.pbf) files")
public class ImportController {

    private final CaopImportService caopImportService;
    private final OsmImportService osmImportService;

    public ImportController(CaopImportService caopImportService,
                           OsmImportService osmImportService) {
        this.caopImportService = caopImportService;
        this.osmImportService = osmImportService;
    }

    @PostMapping("/caop/{folder}")
    @Operation(summary = "Import folder with .gpkg files (development)")
    public ResponseEntity<ImportResult> importFolder(
            @Parameter(description = "Server path to folder with .gpkg files")
            @PathVariable String folder) {
        ImportResult result = caopImportService.importFolder(folder);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/caop/upload")
    @Operation(summary = "Import the full CAOP dataset (all .gpkg files) as one rebuild",
            description = "Upload every CAOP GeoPackage (Continente, Madeira and both Azores "
                    + "groups) in a single multipart request. The files are written to a temp "
                    + "directory and imported via the same clear+merge rebuild as the folder "
                    + "import, so entities split across files (the Azores NUTS) are reassembled.")
    public ResponseEntity<ImportResult> importUpload(
            @Parameter(description = "All CAOP .gpkg files")
            @RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No .gpkg files uploaded");
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("caop_upload_");
            for (int i = 0; i < files.length; i++) {
                files[i].transferTo(tempDir.resolve("upload_" + i + ".gpkg"));
            }
            return ResponseEntity.ok(caopImportService.importFolder(tempDir.toString()));
        } catch (Exception e) {
            throw new IllegalStateException("Upload import failed: " + e.getMessage(), e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /** Best-effort recursive delete of the upload temp directory. */
    private static void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort temp cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort temp cleanup
        }
    }

    @PostMapping("/osm/{folder}")
    @Operation(summary = "Import folder with a single .pbf file via osm2pgsql (development)")
    public ResponseEntity<ImportResult> importOsmFolder(
            @Parameter(description = "Server path to folder containing one .pbf file")
            @PathVariable String folder) {
        ImportResult result = osmImportService.importFolder(folder);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/osm/upload")
    @Operation(summary = "Import .pbf file via osm2pgsql")
    public ResponseEntity<ImportResult> importOsmUpload(
            @RequestParam("file") MultipartFile file) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("osm_", ".pbf");
            file.transferTo(tempFile.toFile());
            ImportResult result = osmImportService.importPbf(tempFile.toString());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            throw new IllegalStateException("Upload failed: " + e.getMessage(), e);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    /** Best-effort delete of the upload temp file. */
    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort temp cleanup
        }
    }
}
