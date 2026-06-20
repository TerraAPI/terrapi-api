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
import pt.terrapi.terrapi_api.service.GeoUnitQueryService;

@RestController
@RequestMapping("/api/v1/hierarchy")
@Tag(name = "Territorial Hierarchies", description = "Fixed endpoints for the administrative (District > Municipality > Parish) and statistical (NUTS1 > NUTS2 > NUTS3) hierarchies")
public class HierarchyController {

    private final GeoUnitQueryService geoUnitQueryService;

    public HierarchyController(GeoUnitQueryService geoUnitQueryService) {
        this.geoUnitQueryService = geoUnitQueryService;
    }

    @GetMapping("/districts")
    @Operation(summary = "List all districts")
    public ResponseEntity<List<GeoUnitSummaryDto>> listDistricts() {
        return ResponseEntity.ok(geoUnitQueryService.findAllByType(GeoUnitType.DISTRICT));
    }

    @GetMapping("/districts/{code}")
    @Operation(summary = "Get district by code")
    public ResponseEntity<GeoUnitSummaryDto> getDistrict(
            @Parameter(description = "District code (DICOFRE)")
            @PathVariable String code) {
        return geoUnitQueryService.findByIdSummary(code)
                .filter(d -> d.type() == GeoUnitType.DISTRICT)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/districts/{code}/municipalities")
    @Operation(summary = "List municipalities in a district")
    public ResponseEntity<List<GeoUnitSummaryDto>> listMunicipalitiesByDistrict(
            @Parameter(description = "District code (DICOFRE)")
            @PathVariable String code) {
        return ResponseEntity.ok(geoUnitQueryService.findChildrenOfType(code, GeoUnitType.MUNICIPALITY));
    }

    @GetMapping("/districts/{code}/parishes")
    @Operation(summary = "List parishes in a district")
    public ResponseEntity<List<GeoUnitSummaryDto>> listParishesByDistrict(
            @Parameter(description = "District code (DICOFRE)")
            @PathVariable String code) {
        return ResponseEntity.ok(geoUnitQueryService.findGrandchildrenOfType(code, GeoUnitType.PARISH));
    }

    @GetMapping("/municipalities")
    @Operation(summary = "List all municipalities")
    public ResponseEntity<List<GeoUnitSummaryDto>> listMunicipalities() {
        return ResponseEntity.ok(geoUnitQueryService.findAllByType(GeoUnitType.MUNICIPALITY));
    }

    @GetMapping("/municipalities/{code}")
    @Operation(summary = "Get municipality by code")
    public ResponseEntity<GeoUnitSummaryDto> getMunicipality(
            @Parameter(description = "Municipality code (DICOFRE)")
            @PathVariable String code) {
        return geoUnitQueryService.findByIdSummary(code)
                .filter(d -> d.type() == GeoUnitType.MUNICIPALITY)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/municipalities/{code}/parishes")
    @Operation(summary = "List parishes in a municipality")
    public ResponseEntity<List<GeoUnitSummaryDto>> listParishesByMunicipality(
            @Parameter(description = "Municipality code (DICOFRE)")
            @PathVariable String code) {
        return ResponseEntity.ok(geoUnitQueryService.findChildrenOfType(code, GeoUnitType.PARISH));
    }

    @GetMapping("/parishes/{code}")
    @Operation(summary = "Get parish by code")
    public ResponseEntity<GeoUnitSummaryDto> getParish(
            @Parameter(description = "Parish code (DICOFRE)")
            @PathVariable String code) {
        return geoUnitQueryService.findByIdSummary(code)
                .filter(d -> d.type() == GeoUnitType.PARISH)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/nuts1")
    @Operation(summary = "List all NUTS1 units")
    public ResponseEntity<List<GeoUnitSummaryDto>> listNuts1() {
        return ResponseEntity.ok(geoUnitQueryService.findAllByType(GeoUnitType.NUTS1));
    }

    @GetMapping("/nuts1/{code}")
    @Operation(summary = "Get NUTS1 unit by code")
    public ResponseEntity<GeoUnitSummaryDto> getNuts1(
            @Parameter(description = "NUTS1 code")
            @PathVariable String code) {
        return geoUnitQueryService.findByIdSummary(code)
                .filter(d -> d.type() == GeoUnitType.NUTS1)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/nuts1/{code}/nuts2")
    @Operation(summary = "List NUTS2 units in a NUTS1 unit")
    public ResponseEntity<List<GeoUnitSummaryDto>> listNuts2ByNuts1(
            @Parameter(description = "NUTS1 code")
            @PathVariable String code) {
        return ResponseEntity.ok(geoUnitQueryService.findChildrenOfType(code, GeoUnitType.NUTS2));
    }

    @GetMapping("/nuts1/{code}/nuts3")
    @Operation(summary = "List NUTS3 units in a NUTS1 unit")
    public ResponseEntity<List<GeoUnitSummaryDto>> listNuts3ByNuts1(
            @Parameter(description = "NUTS1 code")
            @PathVariable String code) {
        return ResponseEntity.ok(geoUnitQueryService.findGrandchildrenOfType(code, GeoUnitType.NUTS3));
    }

    @GetMapping("/nuts2")
    @Operation(summary = "List all NUTS2 units")
    public ResponseEntity<List<GeoUnitSummaryDto>> listNuts2() {
        return ResponseEntity.ok(geoUnitQueryService.findAllByType(GeoUnitType.NUTS2));
    }

    @GetMapping("/nuts2/{code}")
    @Operation(summary = "Get NUTS2 unit by code")
    public ResponseEntity<GeoUnitSummaryDto> getNuts2(
            @Parameter(description = "NUTS2 code")
            @PathVariable String code) {
        return geoUnitQueryService.findByIdSummary(code)
                .filter(d -> d.type() == GeoUnitType.NUTS2)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/nuts2/{code}/nuts3")
    @Operation(summary = "List NUTS3 units in a NUTS2 unit")
    public ResponseEntity<List<GeoUnitSummaryDto>> listNuts3ByNuts2(
            @Parameter(description = "NUTS2 code")
            @PathVariable String code) {
        return ResponseEntity.ok(geoUnitQueryService.findChildrenOfType(code, GeoUnitType.NUTS3));
    }

    @GetMapping("/nuts3/{code}")
    @Operation(summary = "Get NUTS3 unit by code")
    public ResponseEntity<GeoUnitSummaryDto> getNuts3(
            @Parameter(description = "NUTS3 code")
            @PathVariable String code) {
        return geoUnitQueryService.findByIdSummary(code)
                .filter(d -> d.type() == GeoUnitType.NUTS3)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/nuts3/{code}/municipalities")
    @Operation(summary = "List municipalities in a NUTS3 unit")
    public ResponseEntity<List<GeoUnitSummaryDto>> listMunicipalitiesByNuts3(
            @Parameter(description = "NUTS3 code")
            @PathVariable String code) {
        return ResponseEntity.ok(geoUnitQueryService.findMunicipalitiesByNuts3(code));
    }
}
