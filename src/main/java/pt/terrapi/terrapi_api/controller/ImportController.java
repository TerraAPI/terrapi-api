package pt.terrapi.terrapi_api.controller;

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

import java.nio.file.Files;
import java.nio.file.Path;

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
    @Operation(summary = "Import .gpkg file")
    public ResponseEntity<ImportResult> importUpload(
            @RequestParam("file") MultipartFile file) {
        try {
            Path tempFile = Files.createTempFile("caop_", ".gpkg");
            file.transferTo(tempFile.toFile());
            ImportResult result = caopImportService.importGpkg(tempFile.toString());
            Files.deleteIfExists(tempFile);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            throw new RuntimeException("Upload failed: " + e.getMessage(), e);
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
        try {
            Path tempFile = Files.createTempFile("osm_", ".pbf");
            file.transferTo(tempFile.toFile());
            ImportResult result = osmImportService.importPbf(tempFile.toString());
            Files.deleteIfExists(tempFile);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            throw new RuntimeException("Upload failed: " + e.getMessage(), e);
        }
    }
}
