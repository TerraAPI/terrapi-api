package pt.terrapi.terrapi_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a point-in-unit containment check")
public record ContainsResponse(
        @Schema(description = "Unit code that was tested")
        String code,
        @Schema(description = "Whether the point lies inside the unit")
        boolean contains
) {}
