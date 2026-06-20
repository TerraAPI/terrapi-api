package pt.terrapi.terrapi_api.dto;

/**
 * Interface-based projection for native spatial queries that return summary rows.
 * The {@code type} column is stored as an integer (see GeoUnitTypeConverter).
 */
public interface GeoUnitSummaryProjection {

    String getCode();

    String getName();

    Integer getType();

    String getParentCode();
}
