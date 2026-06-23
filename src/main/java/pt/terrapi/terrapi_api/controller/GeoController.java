package pt.terrapi.terrapi_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.terrapi.terrapi_api.dto.BatchReverseGeocodeRequest;
import pt.terrapi.terrapi_api.dto.BatchReverseGeocodeResult;
import pt.terrapi.terrapi_api.dto.ContainsResponse;
import pt.terrapi.terrapi_api.dto.GeoJsonFeatureDto;
import pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.service.GeoUnitQueryService;

@RestController
@RequestMapping("/api/v1/geo")
@Tag(name = "Geo", description = "Geographic operations such as reverse geocoding")
public class GeoController {

    private final GeoUnitQueryService geoUnitQueryService;

    public GeoController(GeoUnitQueryService geoUnitQueryService) {
        this.geoUnitQueryService = geoUnitQueryService;
    }

    @GetMapping("/reverse-geocode")
    @Operation(summary = "Reverse geocode a coordinate, returning the containing unit")
    public ResponseEntity<GeoUnitSummaryDto> reverseGeocode(
            @Parameter(description = "Latitude") @RequestParam double lat,
            @Parameter(description = "Longitude") @RequestParam double lon,
            @Parameter(description = "Unit type to resolve (default PARISH)")
            @RequestParam(defaultValue = "PARISH") GeoUnitType type) {
        return ResponseEntity.ok(geoUnitQueryService.reverseGeocode(lat, lon, type));
    }

    @PostMapping("/reverse-geocode/batch")
    @Operation(summary = "Reverse geocode many coordinates in a single request")
    public ResponseEntity<List<BatchReverseGeocodeResult>> batchReverseGeocode(
            @RequestBody BatchReverseGeocodeRequest request) {
        return ResponseEntity.ok(geoUnitQueryService.batchReverseGeocode(request));
    }

    @GetMapping("/contains")
    @Operation(summary = "Check whether a coordinate lies inside a specific unit")
    public ResponseEntity<ContainsResponse> contains(
            @Parameter(description = "Unit code (DICOFRE)") @RequestParam String code,
            @Parameter(description = "Latitude") @RequestParam double lat,
            @Parameter(description = "Longitude") @RequestParam double lon) {
        return ResponseEntity.ok(geoUnitQueryService.pointInside(code, lat, lon));
    }

    @GetMapping("/{code}/geometry")
    @Operation(summary = "Get a unit's boundary as a GeoJSON Feature")
    public ResponseEntity<GeoJsonFeatureDto> geometry(
            @Parameter(description = "Unit code (DICOFRE)") @PathVariable String code,
            @Parameter(description = "Simplification tolerance in degrees (0 = full detail)")
            @RequestParam(defaultValue = "0") double tolerance) {
        return ResponseEntity.ok(geoUnitQueryService.geometryAsGeoJson(code, tolerance));
    }

    @GetMapping("/within-bbox")
    @Operation(summary = "List units of a type intersecting a bounding box (map viewport)")
    public ResponseEntity<List<GeoUnitSummaryDto>> withinBbox(
            @Parameter(description = "Minimum longitude") @RequestParam double minLon,
            @Parameter(description = "Minimum latitude") @RequestParam double minLat,
            @Parameter(description = "Maximum longitude") @RequestParam double maxLon,
            @Parameter(description = "Maximum latitude") @RequestParam double maxLat,
            @Parameter(description = "Unit type to return") @RequestParam GeoUnitType type) {
        return ResponseEntity.ok(geoUnitQueryService.findWithinBbox(type, minLon, minLat, maxLon, maxLat));
    }

    @GetMapping("/within")
    @Operation(summary = "List units of a type within a radius of a coordinate (nearest first)")
    public ResponseEntity<List<GeoUnitSummaryDto>> within(
            @Parameter(description = "Latitude") @RequestParam double lat,
            @Parameter(description = "Longitude") @RequestParam double lon,
            @Parameter(description = "Radius in kilometers") @RequestParam double radiusKm,
            @Parameter(description = "Unit type to return") @RequestParam GeoUnitType type) {
        return ResponseEntity.ok(geoUnitQueryService.findWithinRadius(type, lat, lon, radiusKm));
    }
}
