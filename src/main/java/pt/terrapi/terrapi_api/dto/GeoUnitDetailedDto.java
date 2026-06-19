package pt.terrapi.terrapi_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

@Schema(description = "Unidade geografica")
public record GeoUnitDetailedDto(
        @Schema(description = "Codigo da unidade (DICOFRE)")
        String code,
        @Schema(description = "Nome da unidade")
        String name,
        @Schema(description = "Nome simplificado")
        String simplifiedName,
        @Schema(description = "Tipo de unidade geografica")
        GeoUnitType type,
        @Schema(description = "Codigo da unidade pai")
        String parentCode,
        @Schema(description = "Codigo NUTS III")
        String nuts3Code,
        @Schema(description = "Area em hectares")
        Double areaHa,
        @Schema(description = "Perimetro em quilometros")
        Double perimeterKm
) {}
