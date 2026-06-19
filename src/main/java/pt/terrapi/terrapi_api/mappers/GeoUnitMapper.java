package pt.terrapi.terrapi_api.mappers;

import java.util.List;
import pt.terrapi.terrapi_api.dto.GeoUnitDetailedDto;
import pt.terrapi.terrapi_api.dto.GeoUnitMinimalDto;
import pt.terrapi.terrapi_api.entities.GeoUnit;

public final class GeoUnitMapper {

    private GeoUnitMapper() {}

    public static GeoUnitDetailedDto toDetailedDto(GeoUnit entity) {
        return new GeoUnitDetailedDto(
                entity.getCode(),
                entity.getName(),
                entity.getSimplifiedName(),
                entity.getType(),
                entity.getParent() != null ? entity.getParent().getCode() : null,
                entity.getNuts3Code(),
                entity.getAreaHa(),
                entity.getPerimeterKm()
        );
    }

    public static List<GeoUnitDetailedDto> toDetailedDtoList(List<GeoUnit> entities) {
        return entities.stream().map(GeoUnitMapper::toDetailedDto).toList();
    }

    public static GeoUnitMinimalDto toMinimalDto(GeoUnit entity) {
        return new GeoUnitMinimalDto(
                entity.getCode(),
                entity.getName(),
                entity.getSimplifiedName(),
                entity.getType(),
                entity.getParent() != null ? entity.getParent().getCode() : null
        );
    }

    public static List<GeoUnitMinimalDto> toMinimalDtoList(List<GeoUnit> entities) {
        return entities.stream().map(GeoUnitMapper::toMinimalDto).toList();
    }

    public static GeoUnit toEntity(GeoUnitDetailedDto dto) {
        GeoUnit entity = new GeoUnit();
        applyTo(dto, entity);
        return entity;
    }

    public static void applyTo(GeoUnitDetailedDto dto, GeoUnit target) {
        target.setCode(dto.code());
        target.setName(dto.name());
        target.setSimplifiedName(dto.simplifiedName());
        target.setType(dto.type());
        target.setNuts3Code(dto.nuts3Code());
        target.setAreaHa(dto.areaHa());
        target.setPerimeterKm(dto.perimeterKm());
    }
}
