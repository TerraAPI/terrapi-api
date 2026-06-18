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
import pt.terrapi.terrapi_api.service.CaopImportService;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/import")
@Tag(name = "Importação", description = "Importação de ficheiros CAOP (GeoPackage)")
public class ImportController {

    private final CaopImportService caopImportService;

    public ImportController(CaopImportService caopImportService) {
        this.caopImportService = caopImportService;
    }

    @PostMapping("/caop/{folder}")
    @Operation(summary = "Importar pasta com ficheiros .gpkg (desenvolvimento)")
    public ResponseEntity<ImportResult> importFolder(
            @Parameter(description = "Caminho no servidor para a pasta com ficheiros .gpkg")
            @PathVariable String folder) {
        ImportResult result = caopImportService.importFolder(folder);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/caop/upload")
    @Operation(summary = "Importar ficheiro .gpkg")
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
}
