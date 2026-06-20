package pt.terrapi.terrapi_api.service;

import java.util.ArrayList;
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
import pt.terrapi.terrapi_api.dto.ReverseGeocodeResponse;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.enums.ReverseGeocodeScope;
import pt.terrapi.terrapi_api.mappers.GeoUnitMapper;
import pt.terrapi.terrapi_api.repository.GeoUnitRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GeoUnitQueryService {

    private final GeoUnitRepository geoUnitRepository;

    public PagedResponse<GeoUnitSummaryDto> findAll(Pageable pageable) {
        return PagedResponse.from(geoUnitRepository.findAllMinimal(pageable));
    }

    public PagedResponse<GeoUnitSummaryDto> findByType(GeoUnitType type, Pageable pageable) {
        return PagedResponse.from(geoUnitRepository.findByTypeMinimal(type, pageable));
    }

    public Optional<GeoUnitDetailsDto> findById(String code) {
        return geoUnitRepository.findById(code)
                .map(GeoUnitMapper::toDetailedDto);
    }

    public List<GeoUnitSummaryDto> findChildren(String code) {
        if (!geoUnitRepository.existsById(code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + code);
        }
        return geoUnitRepository.findByParentCodeMinimal(code);
    }

    public List<GeoUnitSummaryDto> findAllByType(GeoUnitType type) {
        return geoUnitRepository.findByTypeList(type);
    }

    public Optional<GeoUnitSummaryDto> findByIdSummary(String code) {
        return geoUnitRepository.findById(code)
                .map(GeoUnitMapper::toMinimalDto);
    }

    public List<GeoUnitSummaryDto> findChildrenOfType(String parentCode, GeoUnitType type) {
        if (!geoUnitRepository.existsById(parentCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + parentCode);
        }
        return geoUnitRepository.findByParentCodeAndTypeMinimal(parentCode, type);
    }

    public List<GeoUnitSummaryDto> findGrandchildrenOfType(String grandparentCode, GeoUnitType type) {
        if (!geoUnitRepository.existsById(grandparentCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + grandparentCode);
        }
        return geoUnitRepository.findByGrandparentCodeAndTypeMinimal(grandparentCode, type);
    }

    public List<GeoUnitSummaryDto> findMunicipalitiesByNuts3(String nuts3Code) {
        if (!geoUnitRepository.existsById(nuts3Code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + nuts3Code);
        }
        return geoUnitRepository.findByNuts3CodeMinimal(nuts3Code);
    }

    public ReverseGeocodeResponse reverseGeocode(double lat, double lon, ReverseGeocodeScope scope) {
        String code = geoUnitRepository.findContainingCode(lon, lat, GeoUnitType.PARISH.getValue())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No geographic unit found at this location"));
        GeoUnit parish = geoUnitRepository.findById(code).orElseThrow();

        return switch (scope) {
            case ADMIN -> {
                List<GeoUnitSummaryDto> ancestors = new ArrayList<>();
                GeoUnit current = parish;
                while (current.getParent() != null) {
                    current = current.getParent();
                    if (!current.getType().isAdministrative()) break;
                    ancestors.add(GeoUnitMapper.toMinimalDto(current));
                }
                yield new ReverseGeocodeResponse(GeoUnitMapper.toMinimalDto(parish), ancestors);
            }
            case ADMIN_NUTS -> {
                List<GeoUnitSummaryDto> ancestors = new ArrayList<>();
                GeoUnit municipality = parish.getParent();
                if (municipality != null && municipality.getType() == GeoUnitType.MUNICIPALITY) {
                    ancestors.add(GeoUnitMapper.toMinimalDto(municipality));
                    String nuts3Code = municipality.getNuts3Code();
                    if (nuts3Code != null) {
                        geoUnitRepository.findById(nuts3Code).ifPresent(nuts3Unit -> {
                            GeoUnit n = nuts3Unit;
                            while (n != null) {
                                ancestors.add(GeoUnitMapper.toMinimalDto(n));
                                n = n.getParent();
                            }
                        });
                    }
                }
                yield new ReverseGeocodeResponse(GeoUnitMapper.toMinimalDto(parish), ancestors);
            }
            case NUTS -> {
                GeoUnit municipality = parish.getParent();
                String nuts3Code = municipality != null && municipality.getType() == GeoUnitType.MUNICIPALITY
                        ? municipality.getNuts3Code()
                        : null;
                if (nuts3Code == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "No NUTS unit found at this location");
                }
                GeoUnit nuts3 = geoUnitRepository.findById(nuts3Code)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "No NUTS unit found at this location"));
                List<GeoUnitSummaryDto> ancestors = new ArrayList<>();
                GeoUnit n = nuts3.getParent();
                while (n != null) {
                    ancestors.add(GeoUnitMapper.toMinimalDto(n));
                    n = n.getParent();
                }
                yield new ReverseGeocodeResponse(GeoUnitMapper.toMinimalDto(nuts3), ancestors);
            }
        };
    }
}
