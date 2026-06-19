package pt.terrapi.terrapi_api.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.terrapi_api.dto.GeoUnitDetailedDto;
import pt.terrapi.terrapi_api.dto.GeoUnitMinimalDto;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.AncestorScope;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.mappers.GeoUnitMapper;
import pt.terrapi.terrapi_api.repository.GeoUnitRepository;

@Service
@RequiredArgsConstructor
public class GeoUnitService {

    private final GeoUnitRepository geoUnitRepository;

    @Transactional(readOnly = true)
    public List<GeoUnitMinimalDto> findAll() {
        return geoUnitRepository.findAllMinimal();
    }

    @Transactional(readOnly = true)
    public List<GeoUnitMinimalDto> findByType(GeoUnitType type) {
        return geoUnitRepository.findByTypeMinimal(type);
    }

    @Transactional(readOnly = true)
    public Optional<GeoUnitDetailedDto> findById(String code) {
        return geoUnitRepository.findById(code)
                .map(GeoUnitMapper::toDetailedDto);
    }

    @Transactional(readOnly = true)
    public List<GeoUnitMinimalDto> findChildren(String code) {
        return geoUnitRepository.findByParentCodeMinimal(code);
    }

    @Transactional(readOnly = true)
    public List<GeoUnitMinimalDto> findAncestors(String code, AncestorScope scope) {
        if (scope == AncestorScope.DIRECT) {
            return geoUnitRepository.findByIdWithParent(code)
                    .map(GeoUnit::getParent)
                    .map(GeoUnitMapper::toMinimalDto)
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }
        List<GeoUnit> ancestors = geoUnitRepository.findAncestorsRecursive(code);
        List<GeoUnitMinimalDto> dtos = GeoUnitMapper.toMinimalDtoList(ancestors);
        return switch (scope) {
            case ADMIN -> dtos.stream().filter(d -> d.type().isAdministrative()).toList();
            case NUTS -> dtos.stream().filter(d -> d.type().isStatistical()).toList();
            default -> dtos;
        };
    }
}
