package pt.terrapi.terrapi_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.terrapi.terrapi_api.dto.GeoUnitDetailsDto;
import pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto;
import pt.terrapi.terrapi_api.dto.PagedResponse;
import pt.terrapi.terrapi_api.enums.AncestorScope;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.service.GeoUnitService;

@RestController
@RequestMapping("/api/v1/geo-units")
@Tag(name = "Unidades Geograficas", description = "Consulta de unidades geograficas e hierarquia")
public class GeoUnitController {

    private final GeoUnitService geoUnitService;

    public GeoUnitController(GeoUnitService geoUnitService) {
        this.geoUnitService = geoUnitService;
    }

    @GetMapping
    @Operation(summary = "Listar unidades geograficas, opcionalmente filtradas por tipo")
    public ResponseEntity<PagedResponse<GeoUnitSummaryDto>> findAll(
            @Parameter(description = "Filtrar por tipo: DISTRICT, MUNICIPALITY, PARISH, ISLAND, NUTS1, NUTS2, NUTS3")
            @RequestParam(required = false) GeoUnitType type,
            @ParameterObject @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        if (type != null) {
            return ResponseEntity.ok(geoUnitService.findByType(type, pageable));
        }
        return ResponseEntity.ok(geoUnitService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obter unidade geografica por codigo")
    public ResponseEntity<GeoUnitDetailsDto> findById(
            @Parameter(description = "Codigo da unidade geografica (DICOFRE)")
            @PathVariable String id) {
        return geoUnitService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "Listar unidades filhas (descendentes diretos)")
    public ResponseEntity<List<GeoUnitSummaryDto>> findChildren(
            @Parameter(description = "Codigo da unidade geografica pai")
            @PathVariable String id) {
        return ResponseEntity.ok(geoUnitService.findChildren(id));
    }

    @GetMapping("/{id}/ancestors")
    @Operation(summary = "Listar unidades ancestrais")
    public ResponseEntity<List<GeoUnitSummaryDto>> findAncestors(
            @Parameter(description = "Codigo da unidade geografica")
            @PathVariable String id,
            @Parameter(description = "Ambito: DIRECT, ADMIN, ADMIN_AND_NUTS, NUTS")
            @RequestParam(defaultValue = "ADMIN_AND_NUTS") AncestorScope scope) {
        return ResponseEntity.ok(geoUnitService.findAncestors(id, scope));
    }
}
