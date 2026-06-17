package pt.terrapi.terrapi_api.dto;

public record ImportResult(int adminUnits, int statUnits) {

    public ImportResult add(ImportResult other) {
        return new ImportResult(
                this.adminUnits + other.adminUnits,
                this.statUnits + other.statUnits
        );
    }
}
