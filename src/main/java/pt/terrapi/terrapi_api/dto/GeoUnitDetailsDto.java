package pt.terrapi.terrapi_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

@Schema(description = "Geographic unit")
public record GeoUnitDetailsDto(
        @Schema(description = "Unit code (DICOFRE)")
        String code,
        @Schema(description = "Unit name")
        String name,
        @Schema(description = "Simplified name (parish only)")
        String simplifiedName,
        @Schema(description = "Geographic unit type")
        GeoUnitType type,

        @Schema(description = "Parent unit")
        GeoUnitSummaryDto parent,

        @Schema(description = "NUTS III code")
        String nuts3Code,
        @Schema(description = "Area in hectares")
        Double areaHa,
        @Schema(description = "Perimeter in kilometers")
        Double perimeterKm
) {}
