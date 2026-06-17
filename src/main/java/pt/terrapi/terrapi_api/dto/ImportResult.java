package pt.terrapi.terrapi_api.dto;

import lombok.Getter;

@Getter
public class ImportResult {
    private final int nuts1;
    private final int nuts2;
    private final int nuts3;
    private final int districts;
    private final int municipalities;
    private final int parishes;

    public ImportResult(int nuts1, int nuts2, int nuts3,
                        int districts, int municipalities, int parishes) {
        this.nuts1 = nuts1;
        this.nuts2 = nuts2;
        this.nuts3 = nuts3;
        this.districts = districts;
        this.municipalities = municipalities;
        this.parishes = parishes;
    }
}
