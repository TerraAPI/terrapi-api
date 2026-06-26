package pt.terrapi.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

@Schema(description = "Batch reverse-geocode request")
public record BatchReverseGeocodeRequest(
        @Schema(description = "Points to reverse geocode")
        List<PointDto> points,
        @Schema(description = "Unit type to resolve for every point (default PARISH)")
        GeoUnitType type
) {}
