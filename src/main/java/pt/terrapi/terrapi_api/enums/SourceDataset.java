package pt.terrapi.terrapi_api.enums;

import lombok.Getter;

@Getter
public enum SourceDataset {
    CONTINENTE("cont_", 3763),
    AZORES_WEST("raa_oci_", 5014),
    AZORES_EAST("raa_cen_ori_", 5015),
    MADEIRA("ram_", 5016);

    private final String prefix;
    private final int sourceEpsg;

    SourceDataset(String prefix, int sourceEpsg) {
        this.prefix = prefix;
        this.sourceEpsg = sourceEpsg;
    }

    public static SourceDataset fromPrefix(String prefix) {
        for (SourceDataset ds : values()) {
            if (ds.prefix.equals(prefix)) {
                return ds;
            }
        }
        throw new IllegalArgumentException("Unknown source dataset for prefix: " + prefix);
    }

    public int validateSrid(int detected) {
        if (detected != sourceEpsg) {
            throw new IllegalStateException(
                    "CRS mismatch for " + name() + ": expected " + sourceEpsg
                            + " but GPKG metadata reports " + detected);
        }
        return sourceEpsg;
    }
}
