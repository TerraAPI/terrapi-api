package pt.terrapi.terrapi_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.Map;

public record ImportResult(
        @Schema(description = "Counts by geographic unit type (DISTRICT, MUNICIPALITY, PARISH, ISLAND, NUTS1, NUTS2, NUTS3)")
        Map<String, Integer> counts
) {

    public ImportResult {
        counts = Map.copyOf(counts);
    }

    public static ImportResult empty() {
        return new ImportResult(Map.of());
    }

    public ImportResult add(ImportResult other) {
        Map<String, Integer> merged = new HashMap<>(counts);
        other.counts.forEach((k, v) -> merged.merge(k, v, Integer::sum));
        return new ImportResult(merged);
    }

    public int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
