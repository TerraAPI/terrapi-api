package pt.terrapi.terrapi_api.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.terrapi_api.dto.GeoUnitDto;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.AncestorScope;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.mappers.GeoUnitMapper;
import pt.terrapi.terrapi_api.repository.GeoUnitRepository;

@Service
@RequiredArgsConstructor
public class GeoUnitService {

    private static final Set<GeoUnitType> ADMIN_TYPES = Set.of(
            GeoUnitType.DISTRICT, GeoUnitType.MUNICIPALITY, GeoUnitType.PARISH, GeoUnitType.ISLAND);
    private static final Set<GeoUnitType> NUTS_TYPES = Set.of(
            GeoUnitType.NUTS1, GeoUnitType.NUTS2, GeoUnitType.NUTS3);

    private final GeoUnitRepository geoUnitRepository;

    @Transactional(readOnly = true)
    public List<GeoUnitDto> findAll() {
        return GeoUnitMapper.toDtoList(geoUnitRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<GeoUnitDto> findByType(GeoUnitType type) {
        return GeoUnitMapper.toDtoList(geoUnitRepository.findByType(type));
    }

    @Transactional(readOnly = true)
    public Optional<GeoUnitDto> findById(String code) {
        return geoUnitRepository.findById(code)
                .map(GeoUnitMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<GeoUnitDto> findChildren(String code) {
        return GeoUnitMapper.toDtoList(geoUnitRepository.findByParentCode(code));
    }

    @Transactional(readOnly = true)
    public List<GeoUnitDto> findAncestors(String code, AncestorScope scope) {
        if (scope == AncestorScope.DIRECT) {
            return geoUnitRepository.findByIdWithParent(code)
                    .map(GeoUnit::getParent)
                    .map(GeoUnitMapper::toDto)
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }
        List<GeoUnit> ancestors = geoUnitRepository.findAncestorsRecursive(code);
        List<GeoUnitDto> dtos = GeoUnitMapper.toDtoList(ancestors);
        return switch (scope) {
            case ADMIN -> dtos.stream().filter(d -> ADMIN_TYPES.contains(d.type())).toList();
            case NUTS -> dtos.stream().filter(d -> NUTS_TYPES.contains(d.type())).toList();
            default -> dtos;
        };
    }
}
