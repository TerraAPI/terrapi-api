package pt.terrapi.terrapi_api.controller;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.service.LayerService;

@RestController
@RequestMapping("/api/v1/layers")
@Tag(name = "Layers", description = "Whole-layer selection grids (GeoJSON)")
public class LayerController {

    private static final String GEOJSON_TYPE = "application/geo+json";

    private final LayerService layerService;

    public LayerController(LayerService layerService) {
        this.layerService = layerService;
    }

    @GetMapping("/{type}")
    @Operation(summary = "Get all units of a type as a GeoJSON FeatureCollection for selection",
            description = "Selection-grade simplified geometry (LOD 0 = most detailed, 4 = coarsest). "
                    + "Fetch the precise boundary of a chosen unit via the per-unit geometry endpoint.")
    public ResponseEntity<String> getLayer(
            @Parameter(description = "Unit type (DISTRICT, MUNICIPALITY, PARISH, ISLAND, NUTS1, NUTS2, NUTS3)")
            @PathVariable GeoUnitType type,
            @Parameter(description = "Precision LOD level (0 = most detailed, 4 = coarsest)")
            @RequestParam(defaultValue = "4") int lod,
            @Parameter(description = "Optional parent code to return only that parent's children")
            @RequestParam(required = false) String parent,
            HttpServletRequest request) {

        String etag = layerService.getETag(type, lod, parent);
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);

        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS).cachePublic().immutable())
                    .build();
        }

        String body = layerService.renderLayer(type, lod, parent);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(GEOJSON_TYPE))
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS).cachePublic().immutable())
                .body(body);
    }
}
