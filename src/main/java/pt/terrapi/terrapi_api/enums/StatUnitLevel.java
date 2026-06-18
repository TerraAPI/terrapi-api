package pt.terrapi.terrapi_api.enums;

public enum StatUnitLevel {
    NUTS1(1),
    NUTS2(2),
    NUTS3(3);

    private final int value;

    StatUnitLevel(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static StatUnitLevel fromValue(int value) {
        for (StatUnitLevel level : values()) {
            if (level.value == value) return level;
        }
        throw new IllegalArgumentException("Unknown StatUnitLevel value: " + value);
    }
}
