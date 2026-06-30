package pt.terrapi.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result for a single code in a batch geo-unit request")
public record BatchGeoUnitResult(
        @Schema(description = "The requested code")
        String code,
        @Schema(description = "Whether the unit was found")
        boolean found,
        @Schema(description = "The geographic unit, null when not found")
        GeoUnitSummaryDto unit
) {}
