package pt.terrapi.terrapi_api.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.terrapi.terrapi_api.dto.GeoUnitDetailsDto;
import pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto;
import pt.terrapi.terrapi_api.dto.PagedResponse;
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
    public PagedResponse<GeoUnitSummaryDto> findAll(Pageable pageable) {
        return PagedResponse.from(geoUnitRepository.findAllMinimal(pageable));
    }

    @Transactional(readOnly = true)
    public PagedResponse<GeoUnitSummaryDto> findByType(GeoUnitType type, Pageable pageable) {
        return PagedResponse.from(geoUnitRepository.findByTypeMinimal(type, pageable));
    }

    @Transactional(readOnly = true)
    public Optional<GeoUnitDetailsDto> findById(String code) {
        return geoUnitRepository.findById(code)
                .map(GeoUnitMapper::toDetailedDto);
    }

    @Transactional(readOnly = true)
    public List<GeoUnitSummaryDto> findChildren(String code) {
        return geoUnitRepository.findByParentCodeMinimal(code);
    }

    @Transactional(readOnly = true)
    public List<GeoUnitSummaryDto> findAllByType(GeoUnitType type) {
        return geoUnitRepository.findByTypeList(type);
    }

    @Transactional(readOnly = true)
    public Optional<GeoUnitSummaryDto> findByIdSummary(String code) {
        return geoUnitRepository.findById(code)
                .map(GeoUnitMapper::toMinimalDto);
    }

    @Transactional(readOnly = true)
    public List<GeoUnitSummaryDto> findChildrenOfType(String parentCode, GeoUnitType type) {
        return geoUnitRepository.findByParentCodeAndTypeMinimal(parentCode, type);
    }

    @Transactional(readOnly = true)
    public List<GeoUnitSummaryDto> findAncestors(String code, AncestorScope scope) {
        if (scope == AncestorScope.DIRECT) {
            return geoUnitRepository.findByIdWithParent(code)
                    .map(GeoUnit::getParent)
                    .map(GeoUnitMapper::toMinimalDto)
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }
        List<GeoUnit> ancestors = geoUnitRepository.findAncestorsRecursive(code);
        List<GeoUnitSummaryDto> dtos = GeoUnitMapper.toMinimalDtoList(ancestors);
        return switch (scope) {
            case ADMIN -> dtos.stream().filter(d -> d.type().isAdministrative()).toList();
            case NUTS -> dtos.stream().filter(d -> d.type().isStatistical()).toList();
            default -> dtos;
        };
    }
}
