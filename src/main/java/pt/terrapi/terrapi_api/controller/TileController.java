package pt.terrapi.terrapi_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import pt.terrapi.terrapi_api.service.TileService;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/tiles")
@Tag(name = "Tiles", description = "Mapbox Vector Tiles (MVT)")
public class TileController {

    private static final String MVT_TYPE = "application/vnd.mapbox-vector-tile";

    private final TileService tileService;

    public TileController(TileService tileService) {
        this.tileService = tileService;
    }

    @GetMapping("/{z}/{x}/{y}")
    @Operation(summary = "Get a Mapbox Vector Tile for the given type and LOD")
    public ResponseEntity<byte[]> getTile(
            @Parameter(description = "Zoom level (0–22)")
            @PathVariable int z,
            @Parameter(description = "Tile X coordinate")
            @PathVariable int x,
            @Parameter(description = "Tile Y coordinate")
            @PathVariable int y,
            @Parameter(description = "Unit type")
            @RequestParam GeoUnitType type,
            @Parameter(description = "Precision LOD level (1 = highest detail)")
            @RequestParam(defaultValue = "1") int lod,
            HttpServletRequest request) {

        String etag = tileService.getETag(type, lod);
        String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);

        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS)
                            .cachePublic()
                            .immutable())
                    .build();
        }

        byte[] tile = tileService.renderTile(z, x, y, type, lod);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MVT_TYPE))
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS)
                        .cachePublic()
                        .immutable())
                .body(tile);
    }
}
