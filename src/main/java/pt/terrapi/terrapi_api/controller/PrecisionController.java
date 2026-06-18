package pt.terrapi.terrapi_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.terrapi.terrapi_api.dto.GenerationResult;
import pt.terrapi.terrapi_api.enums.GenerationType;
import pt.terrapi.terrapi_api.service.PrecisionGenerationService;

@RestController
@RequestMapping("/api/v1/precision")
@Tag(name = "Precisão", description = "Geração de geometrias simplificadas (LOD)")
public class PrecisionController {

    private final PrecisionGenerationService precisionGenerationService;

    public PrecisionController(PrecisionGenerationService precisionGenerationService) {
        this.precisionGenerationService = precisionGenerationService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Gerar geometrias simplificadas para os tipos de unidade especificados")
    public ResponseEntity<GenerationResult> generate(
            @Parameter(description = "Tipo de unidade: ALL, DISTRICT, MUNICIPALITY, PARISH, ISLAND, NUTS1, NUTS2, NUTS3")
            @RequestParam(defaultValue = "ALL") GenerationType type) {
        GenerationResult result = precisionGenerationService.generate(type);
        return ResponseEntity.ok(result);
    }
}
