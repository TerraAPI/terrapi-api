package pt.terrapi.core.mappers;

import java.util.List;
import org.locationtech.jts.geom.Point;
import pt.terrapi.core.dto.GeoUnitDetailsDto;
import pt.terrapi.core.dto.GeoUnitSummaryDto;
import pt.terrapi.core.dto.GeoUnitSummaryProjection;
import pt.terrapi.core.entities.GeoUnit;
import pt.terrapi.core.enums.GeoUnitType;

public final class GeoUnitMapper {

    private GeoUnitMapper() {}

    public static GeoUnitDetailsDto toDetailedDto(GeoUnit entity) {
        Point point = entity.getRepresentativePoint();
        Point adminCenter = entity.getAdministrativeCenter();
        return new GeoUnitDetailsDto(
                entity.getCode(),
                entity.getName(),
                entity.getSimplifiedName(),
                entity.getType(),
                entity.getParent() != null ? toMinimalDto(entity.getParent()) : null,
                entity.getNuts1Code(),
                entity.getNuts3Code(),
                entity.getAreaHa(),
                entity.getPerimeterKm(),
                entity.getMunicipalityCount(),
                entity.getParishCount(),
                entity.getCoastlineKm(),
                point != null ? point.getX() : null,
                point != null ? point.getY() : null,
                adminCenter != null ? adminCenter.getX() : null,
                adminCenter != null ? adminCenter.getY() : null
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

    public static GeoUnitSummaryDto toSummaryDto(GeoUnitSummaryProjection projection) {
        return new GeoUnitSummaryDto(
                projection.getCode(),
                projection.getName(),
                GeoUnitType.fromValue(projection.getType()),
                projection.getParentCode()
        );
    }

    public static List<GeoUnitSummaryDto> toSummaryDtoList(List<GeoUnitSummaryProjection> projections) {
        return projections.stream().map(GeoUnitMapper::toSummaryDto).toList();
    }
}
