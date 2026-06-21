package pt.terrapi.terrapi_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Geographic coordinate (WGS84)")
public record PointDto(
        @Schema(description = "Latitude")
        double lat,
        @Schema(description = "Longitude")
        double lon
) {}
