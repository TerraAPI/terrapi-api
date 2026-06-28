package pt.terrapi.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pt.terrapi.core.enums.GeoUnitType;

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

        @Schema(description = "NUTS I code")
        String nuts1Code,
        @Schema(description = "NUTS III code")
        String nuts3Code,
        @Schema(description = "Area in hectares")
        Double areaHa,
        @Schema(description = "Perimeter in kilometers")
        Double perimeterKm,

        @Schema(description = "Number of child municipalities (null for leaf types)")
        Integer municipalityCount,
        @Schema(description = "Number of child parishes (null for parishes)")
        Integer parishCount,
        @Schema(description = "Total coastline length in kilometers (null if landlocked)")
        Double coastlineKm,
        @Schema(description = "Longitude of the interior representative point")
        Double lon,
        @Schema(description = "Latitude of the interior representative point")
        Double lat
) {}
