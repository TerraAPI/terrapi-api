package pt.terrapi.core.service.osm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import pt.terrapi.core.config.ValhallaProperties;

/**
 * Computes isochrones by delegating to the Valhalla routing service ({@code /isochrone}).
 * Valhalla owns the routing graph (built from the OSM pbf); this service just shapes the request
 * and returns Valhalla's GeoJSON FeatureCollection of contour polygons.
 */
@Slf4j
@Service
public class IsochroneService {

    private final RestClient client;

    public IsochroneService(ValhallaProperties properties) {
        this.client = RestClient.builder().baseUrl(properties.getUrl()).build();
    }

    /**
     * @param lat     origin latitude (EPSG:4326)
     * @param lon     origin longitude (EPSG:4326)
     * @param minutes travel-time budget
     * @param costing Valhalla costing model (e.g. {@code auto}, {@code pedestrian}, {@code bicycle})
     * @return Valhalla GeoJSON FeatureCollection (polygon contours)
     */
    public String computeGeoJson(double lat, double lon, double minutes, String costing) {
        String request = """
                {"locations":[{"lat":%s,"lon":%s}],"costing":"%s",\
                "contours":[{"time":%s}],"polygons":true}"""
                .formatted(lat, lon, costing, minutes);

        log.info("Isochrone via Valhalla: origin=({},{}) costing={} budget={} min", lat, lon, costing, minutes);
        return client.post()
                .uri("/isochrone")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
    }
}
