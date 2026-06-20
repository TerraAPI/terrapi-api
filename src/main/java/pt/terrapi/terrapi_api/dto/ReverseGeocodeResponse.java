package pt.terrapi.terrapi_api.dto;

import java.util.List;

public record ReverseGeocodeResponse(
        GeoUnitSummaryDto unit,
        List<GeoUnitSummaryDto> ancestors
) {}
