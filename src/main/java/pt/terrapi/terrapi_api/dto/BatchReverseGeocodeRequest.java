package pt.terrapi.terrapi_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import pt.terrapi.terrapi_api.enums.ReverseGeocodeScope;

@Schema(description = "Batch reverse-geocode request")
public record BatchReverseGeocodeRequest(
        @Schema(description = "Points to reverse geocode")
        List<PointDto> points,
        @Schema(description = "Scope applied to every point: ADMIN, ADMIN_NUTS, NUTS")
        ReverseGeocodeScope scope
) {}
