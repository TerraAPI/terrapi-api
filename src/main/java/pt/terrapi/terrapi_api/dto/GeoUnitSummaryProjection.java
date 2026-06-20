package pt.terrapi.terrapi_api.dto;

public interface GeoUnitSummaryProjection {
    String getCode();
    String getSimplifiedName();
    String getName();
    Integer getType();
    String getParentCode();

    default String getDisplayName() {
        return getSimplifiedName() != null ? getSimplifiedName() : getName();
    }
}
