package pt.terrapi.terrapi_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.terrapi.terrapi_api.dto.ReverseGeocodeResponse;
import pt.terrapi.terrapi_api.enums.ReverseGeocodeScope;
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
    @Operation(summary = "Reverse geocode a coordinate, returning the containing parish and its hierarchy")
    public ResponseEntity<ReverseGeocodeResponse> reverseGeocode(
            @Parameter(description = "Latitude") @RequestParam double lat,
            @Parameter(description = "Longitude") @RequestParam double lon,
            @Parameter(description = "Scope: ADMIN, ADMIN_NUTS, NUTS")
            @RequestParam(defaultValue = "ADMIN") ReverseGeocodeScope scope) {
        return ResponseEntity.ok(geoUnitQueryService.reverseGeocode(lat, lon, scope));
    }
}
