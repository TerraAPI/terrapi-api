package pt.terrapi.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.terrapi.core.dto.GenerationResult;
import pt.terrapi.core.entities.PrecisionGeneration;
import pt.terrapi.core.service.precision.PrecisionGenerationService;

@RestController
@RequestMapping("/api/v1/precision")
@Tag(name = "Precision", description = "Generation of simplified geometries (LOD)")
public class PrecisionController {

    private final PrecisionGenerationService precisionGenerationService;

    public PrecisionController(PrecisionGenerationService precisionGenerationService) {
        this.precisionGenerationService = precisionGenerationService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate the full layer hierarchy at the given LOD (or all LODs)")
    public ResponseEntity<GenerationResult> generate(
            @Parameter(description = "LOD level to (re)generate; omit to generate all configured LODs")
            @RequestParam(required = false) Integer lod) {
        return ResponseEntity.ok(precisionGenerationService.generate(lod));
    }

    @GetMapping("/status")
    @Operation(summary = "Latest precision generation run and its status (SUCCESS/FAILED/RUNNING)",
            description = "Surfaces precision health: a FAILED most-recent run means the layer "
                    + "endpoints are serving the previous precisions. Returns 404 if none has run.")
    public ResponseEntity<PrecisionGeneration> status() {
        return ResponseEntity.of(precisionGenerationService.latestGeneration());
    }
}