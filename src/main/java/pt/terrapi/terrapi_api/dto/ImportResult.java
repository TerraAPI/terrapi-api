package pt.terrapi.terrapi_api.dto;

public record ImportResult(int nuts1, int nuts2, int nuts3, int districts, int municipalities, int parishes) {

    public ImportResult add(ImportResult other) {
        return new ImportResult(
                this.nuts1 + other.nuts1,
                this.nuts2 + other.nuts2,
                this.nuts3 + other.nuts3,
                this.districts + other.districts,
                this.municipalities + other.municipalities,
                this.parishes + other.parishes
        );
    }
}
