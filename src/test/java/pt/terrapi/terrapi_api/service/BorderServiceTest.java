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
}
