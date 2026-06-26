package pt.terrapi.core.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.terrapi.core.service.BorderService;

@RestController
@RequestMapping("/api/v1/borders")
@Tag(name = "Borders", description = "Classified administrative boundary lines (GeoJSON)")
public class BorderController {

    private static final String GEOJSON_TYPE = "application/geo+json";

    private final BorderService borderService;

    public BorderController(BorderService borderService) {
        this.borderService = borderService;
    }

    @GetMapping
    @Operation(summary = "Get administrative boundary lines as a GeoJSON FeatureCollection",
            description = "Simplified geometry from the LOD ladder (0 = most detailed, 2 = coarsest). "
                    + "Each feature carries its border order (level 1 = national/top .. 5 = parish), "
                    + "line type (LAND/COAST/WATER) and length in km.")
    public ResponseEntity<String> getBorders(
            @Parameter(description = "Only return borders of this order or coarser (e.g. 3 = district+ borders)")
            @RequestParam(required = false) Integer maxLevel,
            @Parameter(description = "Precision LOD level (0 = most detailed, 2 = coarsest)")
            @RequestParam(defaultValue = "2") int lod,
            HttpServletRequest request) {

        String etag = borderService.getETag(maxLevel, lod);
        if (etag.equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS).cachePublic().immutable())
                    .build();
        }

        String body = borderService.renderBorders(maxLevel, lod);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(GEOJSON_TYPE))
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS).cachePublic().immutable())
                .body(body);
    }
}
