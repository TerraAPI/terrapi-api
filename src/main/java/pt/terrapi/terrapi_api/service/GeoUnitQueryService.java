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
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
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

    public List<GeoUnitSummaryDto> findNeighbours(String code) {
        if (!geoUnitRepository.existsById(code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + code);
        }
        return GeoUnitMapper.toSummaryDtoList(geoUnitRepository.findNeighbours(code));
    }

    public GeoUnitSummaryDto findParent(String code) {
        GeoUnit unit = geoUnitRepository.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + code));
        GeoUnit parent = unit.getParent();
        if (parent == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit has no parent: " + code);
        }
        return GeoUnitMapper.toMinimalDto(parent);
    }

    public List<GeoUnitSummaryDto> findDescendants(String code, GeoUnitType type) {
        GeoUnit unit = geoUnitRepository.findById(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unit not found: " + code));
        if (type == null) {
            return GeoUnitMapper.toSummaryDtoList(geoUnitRepository.findDescendants(code));
        }
        GeoUnitType unitType = unit.getType();
        if (type == unitType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Requested type " + type + " is the same level as unit " + code
                            + " (" + unitType + "); a unit cannot be its own descendant");
        }
        if (!unitType.canHaveDescendantOfType(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Type " + type + " is not a valid descendant level of " + unitType
                            + " for unit " + code);
        }
        return GeoUnitMapper.toSummaryDtoList(geoUnitRepository.findDescendantsByType(code, type.getValue()));
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
        GeoUnitType type = request.type() != null ? request.type() : GeoUnitType.PARISH;
        List<PointDto> points = request.points() != null ? request.points() : List.of();
        List<BatchReverseGeocodeResult> results = new ArrayList<>(points.size());
        for (PointDto point : points) {
            try {
                GeoUnitSummaryDto unit = reverseGeocode(point.lat(), point.lon(), type);
                results.add(new BatchReverseGeocodeResult(point, true, unit));
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

    public GeoUnitSummaryDto reverseGeocode(double lat, double lon, GeoUnitType type) {
        GeoUnitType targetType = type != null ? type : GeoUnitType.PARISH;
        String code = geoUnitRepository.findContainingCode(lon, lat, targetType.getValue())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No geographic unit found at this location"));
        GeoUnit unit = geoUnitRepository.findById(code).orElseThrow();
        return GeoUnitMapper.toMinimalDto(unit);
    }
}
