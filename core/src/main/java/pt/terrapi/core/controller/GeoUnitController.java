package pt.terrapi.core.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

import lombok.RequiredArgsConstructor;
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
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.service.GeoUnitQueryService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/geo-units")
@Tag(name = "Geographic Units", description = "Query geographic units and hierarchy")
public class GeoUnitController {

    private final GeoUnitQueryService geoUnitQueryService;

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

    @GetMapping("/{id}/parent")
    @Operation(summary = "Get the immediate parent unit")
    public ResponseEntity<GeoUnitSummaryDto> findParent(
            @Parameter(description = "Geographic unit code")
            @PathVariable String id) {
        return ResponseEntity.ok(geoUnitQueryService.findParent(id));
    }

    @GetMapping("/{id}/descendants")
    @Operation(summary = "List all descendant units (all levels), optionally filtered by type")
    public ResponseEntity<List<GeoUnitSummaryDto>> findDescendants(
            @Parameter(description = "Geographic unit code")
            @PathVariable String id,
            @Parameter(description = "Filter descendants by type: DISTRICT, MUNICIPALITY, PARISH, ISLAND, NUTS1, NUTS2, NUTS3")
            @RequestParam(required = false) GeoUnitType type) {
        return ResponseEntity.ok(geoUnitQueryService.findDescendants(id, type));
    }

    @GetMapping("/{id}/neighbours")
    @Operation(summary = "List units sharing a border with this unit (same level)")
    public ResponseEntity<List<GeoUnitSummaryDto>> findNeighbours(
            @Parameter(description = "Geographic unit code")
            @PathVariable String id) {
        return ResponseEntity.ok(geoUnitQueryService.findNeighbours(id));
    }

}
