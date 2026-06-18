package pt.terrapi.terrapi_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ImportResult(
        @Schema(description = "Unidades administrativas: distritos/ilhas + municípios + freguesias")
        int adminUnits,
        @Schema(description = "Unidades estatísticas: NUTS I + II + III")
        int statUnits
) {

    public ImportResult add(ImportResult other) {
        return new ImportResult(
                this.adminUnits + other.adminUnits,
                this.statUnits + other.statUnits
        );
    }
}
