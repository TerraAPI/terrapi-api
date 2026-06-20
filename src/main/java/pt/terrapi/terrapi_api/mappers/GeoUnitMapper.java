package pt.terrapi.terrapi_api.mappers;

import java.util.List;
import pt.terrapi.terrapi_api.dto.GeoUnitDetailsDto;
import pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto;
import pt.terrapi.terrapi_api.entities.GeoUnit;

public final class GeoUnitMapper {

    private GeoUnitMapper() {}

    public static GeoUnitDetailsDto toDetailedDto(GeoUnit entity) {
        return new GeoUnitDetailsDto(
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

    public static List<GeoUnitDetailsDto> toDetailedDtoList(List<GeoUnit> entities) {
        return entities.stream().map(GeoUnitMapper::toDetailedDto).toList();
    }

    public static GeoUnitSummaryDto toMinimalDto(GeoUnit entity) {
        return new GeoUnitSummaryDto(
                entity.getCode(),
                entity.getSimplifiedName() != null ? entity.getSimplifiedName() : entity.getName(),
                entity.getType(),
                entity.getParent() != null ? entity.getParent().getCode() : null
        );
    }

    public static List<GeoUnitSummaryDto> toMinimalDtoList(List<GeoUnit> entities) {
        return entities.stream().map(GeoUnitMapper::toMinimalDto).toList();
    }

    public static GeoUnit toEntity(GeoUnitDetailsDto dto) {
        GeoUnit entity = new GeoUnit();
        applyTo(dto, entity);
        return entity;
    }

    public static void applyTo(GeoUnitDetailsDto dto, GeoUnit target) {
        target.setCode(dto.code());
        target.setName(dto.name());
        target.setSimplifiedName(dto.simplifiedName());
        target.setType(dto.type());
        target.setNuts3Code(dto.nuts3Code());
        target.setAreaHa(dto.areaHa());
        target.setPerimeterKm(dto.perimeterKm());
    }
}
