package pt.terrapi.terrapi_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.service.GeoUnitService;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administrative Hierarchy", description = "Fixed endpoints for District > Municipality > Parish hierarchy")
public class AdminController {

    private final GeoUnitService geoUnitService;

    public AdminController(GeoUnitService geoUnitService) {
        this.geoUnitService = geoUnitService;
    }

    @GetMapping("/districts")
    @Operation(summary = "List all districts")
    public ResponseEntity<List<GeoUnitSummaryDto>> listDistricts() {
        return ResponseEntity.ok(geoUnitService.findAllByType(GeoUnitType.DISTRICT));
    }

    @GetMapping("/districts/{code}")
    @Operation(summary = "Get district by code")
    public ResponseEntity<GeoUnitSummaryDto> getDistrict(
            @Parameter(description = "District code (DICOFRE)")
            @PathVariable String code) {
        return geoUnitService.findByIdSummary(code)
                .filter(d -> d.type() == GeoUnitType.DISTRICT)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/districts/{code}/municipalities")
    @Operation(summary = "List municipalities in a district")
    public ResponseEntity<List<GeoUnitSummaryDto>> listMunicipalitiesByDistrict(
            @Parameter(description = "District code (DICOFRE)")
            @PathVariable String code) {
        return ResponseEntity.ok(geoUnitService.findChildrenOfType(code, GeoUnitType.MUNICIPALITY));
    }

    @GetMapping("/districts/{code}/parishes")
    @Operation(summary = "List parishes in a district")
    public ResponseEntity<List<GeoUnitSummaryDto>> listParishesByDistrict(
            @Parameter(description = "District code (DICOFRE)")
            @PathVariable String code) {
        return ResponseEntity.ok(geoUnitService.findGrandchildrenOfType(code, GeoUnitType.PARISH));
    }

    @GetMapping("/municipalities")
    @Operation(summary = "List all municipalities")
    public ResponseEntity<List<GeoUnitSummaryDto>> listMunicipalities() {
        return ResponseEntity.ok(geoUnitService.findAllByType(GeoUnitType.MUNICIPALITY));
    }

    @GetMapping("/municipalities/{code}")
    @Operation(summary = "Get municipality by code")
    public ResponseEntity<GeoUnitSummaryDto> getMunicipality(
            @Parameter(description = "Municipality code (DICOFRE)")
            @PathVariable String code) {
        return geoUnitService.findByIdSummary(code)
                .filter(d -> d.type() == GeoUnitType.MUNICIPALITY)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/municipalities/{code}/parishes")
    @Operation(summary = "List parishes in a municipality")
    public ResponseEntity<List<GeoUnitSummaryDto>> listParishesByMunicipality(
            @Parameter(description = "Municipality code (DICOFRE)")
            @PathVariable String code) {
        return ResponseEntity.ok(geoUnitService.findChildrenOfType(code, GeoUnitType.PARISH));
    }

    @GetMapping("/parishes/{code}")
    @Operation(summary = "Get parish by code")
    public ResponseEntity<GeoUnitSummaryDto> getParish(
            @Parameter(description = "Parish code (DICOFRE)")
            @PathVariable String code) {
        return geoUnitService.findByIdSummary(code)
                .filter(d -> d.type() == GeoUnitType.PARISH)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
