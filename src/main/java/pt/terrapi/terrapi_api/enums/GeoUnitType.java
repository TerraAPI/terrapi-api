package pt.terrapi.terrapi_api.enums;

import lombok.Getter;

@Getter
public enum GeoUnitType {
    DISTRICT(1, GeoUnitCategory.ADMINISTRATIVE),
    MUNICIPALITY(2, GeoUnitCategory.ADMINISTRATIVE),
    PARISH(3, GeoUnitCategory.ADMINISTRATIVE),
    ISLAND(9, GeoUnitCategory.SPECIAL),
    NUTS1(10, GeoUnitCategory.STATISTICAL),
    NUTS2(11, GeoUnitCategory.STATISTICAL),
    NUTS3(12, GeoUnitCategory.STATISTICAL);

    private final int value;
    private final GeoUnitCategory category;

    GeoUnitType(int value, GeoUnitCategory category) {
        this.value = value;
        this.category = category;
    }

    public boolean isAdministrative() {
        return category == GeoUnitCategory.ADMINISTRATIVE;
    }

    public boolean isStatistical() {
        return category == GeoUnitCategory.STATISTICAL;
    }

    public static GeoUnitType fromValue(int value) {
        for (GeoUnitType type : values()) {
            if (type.value == value) return type;
        }
        throw new IllegalArgumentException("Unknown GeoUnitType value: " + value);
    }
}
