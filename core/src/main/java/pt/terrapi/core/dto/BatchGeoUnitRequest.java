package pt.terrapi.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Batch request to fetch multiple geographic units by code")
public record BatchGeoUnitRequest(
        @Schema(description = "List of geographic unit codes (DICOFRE), max 100")
        List<String> codes
) {}
