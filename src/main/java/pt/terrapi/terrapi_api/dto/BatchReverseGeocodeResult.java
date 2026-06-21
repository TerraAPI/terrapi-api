package pt.terrapi.terrapi_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reverse-geocode result for a single point in a batch")
public record BatchReverseGeocodeResult(
        @Schema(description = "The input point")
        PointDto point,
        @Schema(description = "Whether a containing unit was found")
        boolean found,
        @Schema(description = "Reverse geocode result, null when not found")
        ReverseGeocodeResponse result
) {}
