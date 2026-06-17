package pt.terrapi.terrapi_api.dto;

import lombok.Getter;

@Getter
public class ImportResult {
    private final int districts;
    private final int municipalities;
    private final int parishes;

    public ImportResult(int districts, int municipalities, int parishes) {
        this.districts = districts;
        this.municipalities = municipalities;
        this.parishes = parishes;
    }
}
