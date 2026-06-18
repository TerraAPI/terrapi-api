package pt.terrapi.terrapi_api.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import pt.terrapi.terrapi_api.enums.AdminUnitType;

@Converter(autoApply = true)
public class AdminUnitTypeConverter implements AttributeConverter<AdminUnitType, Integer> {

    @Override
    public Integer convertToDatabaseColumn(AdminUnitType type) {
        if (type == null) return null;
        return type.getValue();
    }

    @Override
    public AdminUnitType convertToEntityAttribute(Integer value) {
        if (value == null) return null;
        return AdminUnitType.fromValue(value);
    }
}
