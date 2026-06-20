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
import pt.terrapi.terrapi_api.service.GeoUnitQueryService;

@RestController
@RequestMapping("/api/v1/geo-units")
@Tag(name = "Geographic Units", description = "Query geographic units and hierarchy")
public class GeoUnitController {

    private final GeoUnitQueryService geoUnitQueryService;

    public GeoUnitController(GeoUnitQueryService geoUnitQueryService) {
        this.geoUnitQueryService = geoUnitQueryService;
    }

    @GetMapping
    @Operation(summary = "List geographic units, optionally filtered by type")
    public ResponseEntity<PagedResponse<GeoUnitSummaryDto>> findAll(
            @Parameter(description = "Filter by type: DISTRICT, MUNICIPALITY, PARISH, ISLAND, NUTS1, NUTS2, NUTS3")
            @RequestParam(required = false) GeoUnitType type,
            @ParameterObject @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        if (type != null) {
            return ResponseEntity.ok(geoUnitQueryService.findByType(type, pageable));
        }
        return ResponseEntity.ok(geoUnitQueryService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get geographic unit by code")
    public ResponseEntity<GeoUnitDetailsDto> findById(
            @Parameter(description = "Geographic unit code (DICOFRE)")
            @PathVariable String id) {
        return geoUnitQueryService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "List child units (direct descendants)")
    public ResponseEntity<List<GeoUnitSummaryDto>> findChildren(
            @Parameter(description = "Parent geographic unit code")
            @PathVariable String id) {
        return ResponseEntity.ok(geoUnitQueryService.findChildren(id));
    }

    @GetMapping("/{id}/ancestors")
    @Operation(summary = "List ancestor units")
    public ResponseEntity<List<GeoUnitSummaryDto>> findAncestors(
            @Parameter(description = "Geographic unit code")
            @PathVariable String id,
            @Parameter(description = "Scope: ADMIN, ADMIN_AND_NUTS, NUTS")
            @RequestParam(defaultValue = "ADMIN_AND_NUTS") AncestorScope scope) {
        return ResponseEntity.ok(geoUnitQueryService.findAncestors(id, scope));
    }
}
