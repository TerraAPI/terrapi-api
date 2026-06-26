package pt.terrapi.core.enums;

import lombok.Getter;

@Getter
public enum GeoUnitType {
    DISTRICT(1, GeoUnitCategory.ADMINISTRATIVE),
    MUNICIPALITY(2, GeoUnitCategory.ADMINISTRATIVE),
    PARISH(3, GeoUnitCategory.ADMINISTRATIVE),
    ISLAND(7, GeoUnitCategory.SPECIAL),
    NUTS1(11, GeoUnitCategory.STATISTICAL),
    NUTS2(12, GeoUnitCategory.STATISTICAL),
    NUTS3(13, GeoUnitCategory.STATISTICAL);

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

    /**
     * Whether a unit of this type may have descendants of {@code descendantType}.
     *
     * <p>Two hierarchies are interlinked: the administrative tree (DISTRICT -&gt; MUNICIPALITY -&gt;
     * PARISH, via {@code parent_code}) and the statistical tree (NUTS1 -&gt; NUTS2 -&gt; NUTS3, via
     * {@code parent_code}). They join at NUTS3 -&gt; MUNICIPALITY through the municipality's
     * {@code nuts3_code}, so NUTS units also reach municipalities and parishes. SPECIAL units
     * (e.g. ISLAND) may contain both administrative and statistical descendants.
     */
    public boolean canHaveDescendantOfType(GeoUnitType descendantType) {
        if (descendantType == null) {
            return false;
        }
        return switch (this) {
            case DISTRICT -> descendantType == MUNICIPALITY || descendantType == PARISH;
            case MUNICIPALITY -> descendantType == PARISH;
            case PARISH -> false;
            case NUTS1 -> descendantType == NUTS2 || descendantType == NUTS3
                    || descendantType == MUNICIPALITY || descendantType == PARISH;
            case NUTS2 -> descendantType == NUTS3
                    || descendantType == MUNICIPALITY || descendantType == PARISH;
            case NUTS3 -> descendantType == MUNICIPALITY || descendantType == PARISH;
            case ISLAND -> descendantType.isAdministrative() || descendantType.isStatistical();
        };
    }

    public static GeoUnitType fromValue(int value) {
        for (GeoUnitType type : values()) {
            if (type.value == value) return type;
        }
        throw new IllegalArgumentException("Unknown GeoUnitType value: " + value);
    }
}
