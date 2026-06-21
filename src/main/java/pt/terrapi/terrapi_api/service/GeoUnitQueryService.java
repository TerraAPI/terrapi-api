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
import pt.terrapi.terrapi_api.dto.BatchReverseGeocodeRequest;
import pt.terrapi.terrapi_api.dto.BatchReverseGeocodeResult;
import pt.terrapi.terrapi_api.dto.ContainsResponse;
import pt.terrapi.terrapi_api.dto.GeoJsonFeatureDto;
import pt.terrapi.terrapi_api.dto.GeoUnitDetailsDto;
import pt.terrapi.terrapi_api.dto.GeoUnitSummaryDto;
import pt.terrapi.terrapi_api.dto.PagedResponse;
import pt.terrapi.terrapi_api.dto.PointDto;
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
        return PagedResponse.from(geoUnitRepository.findSummaryPage(pageable));
    }

    public PagedResponse<GeoUnitSummaryDto> findByType(GeoUnitType type, Pageable pageable) {
        return PagedResponse.from(geoUnitRepository.findSummaryPageByType(type, pageable));
    }

    public Optional<GeoUnitDetailsDto> findById(String code) {
        return geoUnitRepository.findById(code)
                .map(GeoUnitMapper::toDetailedDto);
    }

    public List<GeoUnitSummaryDto> findChildren(String code) {
        if (!geoUnitRepository.existsById(code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + code);
        }
        return geoUnitRepository.findSummaryListByParentCode(code);
    }

    public List<GeoUnitSummaryDto> findAllByType(GeoUnitType type) {
        return geoUnitRepository.findSummaryListByType(type);
    }

    public List<GeoUnitSummaryDto> findChildrenOfType(String parentCode, GeoUnitType type) {
        if (!geoUnitRepository.existsById(parentCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + parentCode);
        }
        return geoUnitRepository.findSummaryListByParentCodeAndType(parentCode, type);
    }

    public List<GeoUnitSummaryDto> findGrandchildrenOfType(String grandparentCode, GeoUnitType type) {
        if (!geoUnitRepository.existsById(grandparentCode)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + grandparentCode);
        }
        return geoUnitRepository.findSummaryListByGrandparentCodeAndType(grandparentCode, type);
    }

    public List<GeoUnitSummaryDto> findMunicipalitiesByNuts3(String nuts3Code) {
        if (!geoUnitRepository.existsById(nuts3Code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + nuts3Code);
        }
        return geoUnitRepository.findSummaryListByNuts3Code(nuts3Code);
    }

    public ContainsResponse pointInside(String code, double lat, double lon) {
        if (!geoUnitRepository.existsById(code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + code);
        }
        boolean contains = geoUnitRepository.isPointInside(code, lon, lat).orElse(false);
        return new ContainsResponse(code, contains);
    }

    public GeoJsonFeatureDto geometryAsGeoJson(String code, double tolerance) {
        GeoUnit unit = geoUnitRepository.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + code));
        String geoJson = geoUnitRepository.findGeoJsonByCode(code, tolerance)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unit has no geometry: " + code));
        String name = unit.getSimplifiedName() != null ? unit.getSimplifiedName() : unit.getName();
        return GeoJsonFeatureDto.of(geoJson, unit.getCode(), name, unit.getType());
    }

    public List<GeoUnitSummaryDto> findWithinBbox(GeoUnitType type, double minLon, double minLat,
            double maxLon, double maxLat) {
        return GeoUnitMapper.toSummaryDtoList(
                geoUnitRepository.findSummaryInBbox(type.getValue(), minLon, minLat, maxLon, maxLat));
    }

    public List<GeoUnitSummaryDto> findWithinRadius(GeoUnitType type, double lat, double lon, double radiusKm) {
        return GeoUnitMapper.toSummaryDtoList(
                geoUnitRepository.findSummaryWithinRadius(type.getValue(), lon, lat, radiusKm * 1000.0));
    }

    public List<BatchReverseGeocodeResult> batchReverseGeocode(BatchReverseGeocodeRequest request) {
        ReverseGeocodeScope scope = request.scope() != null ? request.scope() : ReverseGeocodeScope.ADMIN;
        List<PointDto> points = request.points() != null ? request.points() : List.of();
        List<BatchReverseGeocodeResult> results = new ArrayList<>(points.size());
        for (PointDto point : points) {
            try {
                ReverseGeocodeResponse response = reverseGeocode(point.lat(), point.lon(), scope);
                results.add(new BatchReverseGeocodeResult(point, true, response));
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    results.add(new BatchReverseGeocodeResult(point, false, null));
                } else {
                    throw ex;
                }
            }
        }
        return results;
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
