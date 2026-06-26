package pt.terrapi.core.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.terrapi.terrapi_api.service.osm.IsochroneService;

@RestController
@RequestMapping("/api/v1/isochrone")
@Tag(name = "Isochrone", description = "Reachability areas computed by Valhalla")
public class IsochroneController {

    private static final String GEO_JSON = "application/geo+json";

    private static final Map<String, String> COSTING = Map.of(
            "car", "auto",
            "foot", "pedestrian",
            "bike", "bicycle");

    private final IsochroneService isochroneService;

    public IsochroneController(IsochroneService isochroneService) {
        this.isochroneService = isochroneService;
    }

    @GetMapping
    @Operation(summary = "Isochrone as a GeoJSON FeatureCollection (reachable area within N minutes)")
    public ResponseEntity<String> isochrone(
            @Parameter(description = "Origin latitude (EPSG:4326)") @RequestParam double lat,
            @Parameter(description = "Origin longitude (EPSG:4326)") @RequestParam double lon,
            @Parameter(description = "Travel-time budget in minutes") @RequestParam double minutes,
            @Parameter(description = "Travel mode: car, foot or bike")
            @RequestParam(defaultValue = "car") String mode) {

        String costing = COSTING.get(mode.toLowerCase());
        if (costing == null) {
            throw new IllegalArgumentException("Unsupported mode '" + mode + "' (use car, foot or bike).");
        }
        if (minutes <= 0) {
            throw new IllegalArgumentException("minutes must be > 0.");
        }

        String geojson = isochroneService.computeGeoJson(lat, lon, minutes, costing);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(GEO_JSON))
                .body(geojson);
    }
}
