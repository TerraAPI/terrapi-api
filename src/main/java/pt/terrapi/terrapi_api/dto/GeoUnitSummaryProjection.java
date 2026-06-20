package pt.terrapi.terrapi_api.dto;

import pt.terrapi.terrapi_api.enums.GeoUnitType;

public interface GeoUnitSummaryProjection {
    String getCode();
    String getSimplifiedName();
    String getName();
    GeoUnitType getType();
    String getParentCode();

    default String getDisplayName() {
        return getSimplifiedName() != null ? getSimplifiedName() : getName();
    }
}
