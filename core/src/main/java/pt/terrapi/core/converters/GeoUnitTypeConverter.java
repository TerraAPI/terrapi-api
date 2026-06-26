package pt.terrapi.core.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import pt.terrapi.terrapi_api.enums.GeoUnitType;

@Converter(autoApply = true)
public class GeoUnitTypeConverter implements AttributeConverter<GeoUnitType, Integer> {

    @Override
    public Integer convertToDatabaseColumn(GeoUnitType type) {
        if (type == null) return null;
        return type.getValue();
    }

    @Override
    public GeoUnitType convertToEntityAttribute(Integer value) {
        if (value == null) return null;
        return GeoUnitType.fromValue(value);
    }
}
