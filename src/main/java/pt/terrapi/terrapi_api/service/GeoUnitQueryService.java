package pt.terrapi.terrapi_api.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
@Transactional(readOnly = true)
public class GeoUnitQueryService {

    private final GeoUnitRepository geoUnitRepository;

    public PagedResponse<GeoUnitSummaryDto> findAll(Pageable pageable) {
        return PagedResponse.from(GeoUnitMapper.fromProjectionPage(geoUnitRepository.findAllMinimal(pageable)));
    }

    public PagedResponse<GeoUnitSummaryDto> findByType(GeoUnitType type, Pageable pageable) {
        return PagedResponse.from(GeoUnitMapper.fromProjectionPage(geoUnitRepository.findByTypeMinimal(type, pageable)));
    }

    public Optional<GeoUnitDetailsDto> findById(String code) {
        return geoUnitRepository.findById(code)
                .map(GeoUnitMapper::toDetailedDto);
    }

    public List<GeoUnitSummaryDto> findChildren(String code) {
        if (!geoUnitRepository.existsById(code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + code);
        }
        return GeoUnitMapper.fromProjectionList(geoUnitRepository.findByParentCodeMinimal(code));
    }

    public List<GeoUnitSummaryDto> findAllByType(GeoUnitType type) {
        return GeoUnitMapper.fromProjectionList(geoUnitRepository.findByTypeList(type));
    }

    public Optional<GeoUnitSummaryDto> findByIdSummary(String code) {
        return geoUnitRepository.findById(code)
                .map(GeoUnitMapper::toMinimalDto);
    }

    public List<GeoUnitSummaryDto> findChildrenOfType(String parentCode, GeoUnitType type) {
        if (!geoUnitRepository.existsById(parentCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + parentCode);
        }
        return GeoUnitMapper.fromProjectionList(geoUnitRepository.findByParentCodeAndTypeMinimal(parentCode, type));
    }

    public List<GeoUnitSummaryDto> findGrandchildrenOfType(String grandparentCode, GeoUnitType type) {
        if (!geoUnitRepository.existsById(grandparentCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + grandparentCode);
        }
        return GeoUnitMapper.fromProjectionList(geoUnitRepository.findByGrandparentCodeAndTypeMinimal(grandparentCode, type));
    }

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
