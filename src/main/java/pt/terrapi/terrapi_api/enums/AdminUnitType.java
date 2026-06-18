package pt.terrapi.terrapi_api.enums;

public enum AdminUnitType {
    DISTRICT(1),
    MUNICIPALITY(2),
    PARISH(3),
    ISLAND(9);

    private final int value;

    AdminUnitType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static AdminUnitType fromValue(int value) {
        for (AdminUnitType type : values()) {
            if (type.value == value) return type;
        }
        throw new IllegalArgumentException("Unknown AdminUnitType value: " + value);
    }
}
