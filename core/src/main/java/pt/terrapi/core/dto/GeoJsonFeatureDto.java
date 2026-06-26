package pt.terrapi.core.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

@Schema(description = "GeoJSON Feature representing a geographic unit boundary")
public record GeoJsonFeatureDto(
        @Schema(description = "GeoJSON object type", example = "Feature")
        String type,
        @Schema(description = "GeoJSON geometry object")
        @JsonRawValue String geometry,
        @Schema(description = "Feature properties")
        Properties properties
) {

    public static GeoJsonFeatureDto of(String geometry, String code, String name, GeoUnitType unitType) {
        return new GeoJsonFeatureDto("Feature", geometry, new Properties(code, name, unitType));
    }

    @Schema(description = "Geographic unit properties")
    public record Properties(
            @Schema(description = "Unit code (DICOFRE)")
            String code,
            @Schema(description = "Unit name")
            String name,
            @Schema(description = "Geographic unit type")
            GeoUnitType type
    ) {}
}
