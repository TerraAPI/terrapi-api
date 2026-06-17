package pt.terrapi.terrapi_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pt.terrapi.terrapi_api.dto.ImportResult;
import pt.terrapi.terrapi_api.service.CaopImportService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final CaopImportService caopImportService;

    public ImportController(CaopImportService caopImportService) {
        this.caopImportService = caopImportService;
    }

    @PostMapping("/caop")
    public ResponseEntity<ImportResult> importCaop(@RequestBody Map<String, String> body) {
        String filePath = body.getOrDefault("filePath",
                body.getOrDefault("file_path", null));
        if (filePath == null) {
            return ResponseEntity.badRequest().build();
        }
        ImportResult result = caopImportService.importGpkg(filePath);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/caop/upload")
    public ResponseEntity<ImportResult> importCaopUpload(@RequestParam("file") MultipartFile file) {
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
