package pt.terrapi.terrapi_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.terrapi.terrapi_api.service.osm.IsochroneService;

@RestController
@RequestMapping("/api/v1/isochrone")
@Tag(name = "Isochrone", description = "Reachability areas over the OSM routing graph")
public class IsochroneController {

    private static final String GEO_JSON = "application/geo+json";

    private final IsochroneService isochroneService;

    public IsochroneController(IsochroneService isochroneService) {
        this.isochroneService = isochroneService;
    }

    @GetMapping
    @Operation(summary = "Car isochrone as a GeoJSON Feature (reachable area within N minutes)")
    public ResponseEntity<String> isochrone(
            @Parameter(description = "Origin latitude (EPSG:4326)") @RequestParam double lat,
            @Parameter(description = "Origin longitude (EPSG:4326)") @RequestParam double lon,
            @Parameter(description = "Travel-time budget in minutes") @RequestParam double minutes,
            @Parameter(description = "Travel mode (only 'car' supported)")
            @RequestParam(defaultValue = "car") String mode) {

        if (!"car".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Unsupported mode '" + mode + "' (only 'car' is supported).");
        }
        if (minutes <= 0) {
            throw new IllegalArgumentException("minutes must be > 0.");
        }

        String geometry = isochroneService.computeGeoJson(lat, lon, minutes);
        String feature = """
                {"type":"Feature",\
                "properties":{"mode":"car","minutes":%s,"origin":{"lat":%s,"lon":%s}},\
                "geometry":%s}""".formatted(minutes, lat, lon, geometry == null ? "null" : geometry);

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(GEO_JSON))
                .body(feature);
    }
}
