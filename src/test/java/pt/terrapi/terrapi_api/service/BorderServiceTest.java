package pt.terrapi.terrapi_api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BorderServiceTest {

    @Test
    void buildSql_noMaxLevel_selectsAllBorders() {
        String sql = BorderService.buildSql(false);

        assertThat(sql)
                .contains("border_segments")
                .doesNotContain("level <= ?");
    }

    @Test
    void buildSql_maxLevel_addsLevelFilter() {
        String sql = BorderService.buildSql(true);

        assertThat(sql)
                .contains("border_segments")
                .contains("level <= ?");
    }

    @Test
    void buildPrecisionSql_noMaxLevel_selectsByLod() {
        String sql = BorderService.buildPrecisionSql(false);

        assertThat(sql)
                .contains("border_segment_precisions")
                .contains("lod = ?")
                .doesNotContain("level <= ?");
    }

    @Test
    void buildPrecisionSql_maxLevel_addsLevelFilter() {
        String sql = BorderService.buildPrecisionSql(true);

        assertThat(sql)
                .contains("border_segment_precisions")
                .contains("lod = ?")
                .contains("level <= ?");
    }
}
