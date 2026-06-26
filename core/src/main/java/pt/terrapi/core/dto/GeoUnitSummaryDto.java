package pt.terrapi.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

@Schema(description = "Geographic unit (minimal data for listings)")
public record GeoUnitSummaryDto(
        @Schema(description = "Unit code (DICOFRE)")
        String code,
        @Schema(description = "Name (simplified if available, otherwise original name)")
        String name,
        @Schema(description = "Geographic unit type")
        GeoUnitType type,
        @Schema(description = "Parent unit code")
        String parentCode
) {}
