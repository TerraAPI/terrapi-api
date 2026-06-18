package pt.terrapi.terrapi_api.enums;

public enum GeoUnitType {
    DISTRICT(1),
    MUNICIPALITY(2),
    PARISH(3),
    ISLAND(9),
    NUTS1(10),
    NUTS2(11),
    NUTS3(12);

    private final int value;

    GeoUnitType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static GeoUnitType fromValue(int value) {
        for (GeoUnitType type : values()) {
            if (type.value == value) return type;
        }
        throw new IllegalArgumentException("Unknown GeoUnitType value: " + value);
    }
}
