package pt.terrapi.terrapi_api.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import pt.terrapi.terrapi_api.enums.StatUnitLevel;

@Converter(autoApply = true)
public class StatUnitLevelConverter implements AttributeConverter<StatUnitLevel, Integer> {

    @Override
    public Integer convertToDatabaseColumn(StatUnitLevel level) {
        if (level == null) return null;
        return level.getValue();
    }

    @Override
    public StatUnitLevel convertToEntityAttribute(Integer value) {
        if (value == null) return null;
        return StatUnitLevel.fromValue(value);
    }
}
