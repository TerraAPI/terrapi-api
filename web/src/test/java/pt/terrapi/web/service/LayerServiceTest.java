package pt.terrapi.web.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pt.terrapi.core.enums.GeoUnitType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LayerServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LayerService service;

    @BeforeEach
    void setUp() {
        service.initCache();
    }

    private void stubGenerationId(String id) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), (Object[]) any()))
                .thenReturn(id == null ? List.of() : List.of(id));
    }

    @Test
    void buildLayerSql_noParent_servesPrecisionsWithoutTransform() {
        String sql = LayerService.buildLayerSql(false);

        assertThat(sql)
                .contains("geo_unit_precisions")
                .contains("ST_AsGeoJSON(gp.geometry)")
                .doesNotContain("ST_Transform")
                .doesNotContain("u.parent_code = ?");
    }

    @Test
    void buildLayerSql_withParent_addsParentFilter() {
        String sql = LayerService.buildLayerSql(true);

        assertThat(sql)
                .contains("geo_unit_precisions")
                .contains("u.parent_code = ?");
    }

    @Test
    void buildLayerSql_includesEnrichedProperties() {
        String sql = LayerService.buildLayerSql(false);

        assertThat(sql)
                .contains("'type', CASE u.type")
                .contains("'parentCode', u.parent_code")
                .contains("'lon', CASE")
                .contains("'lat', CASE")
                .contains("'municipalityCount', u.municipality_count")
                .contains("'parishCount', u.parish_count");
    }

    @Test
    void buildLayerSql_neverReadsOriginalGeoUnitsTable() {
        assertThat(LayerService.buildLayerSql(false)).doesNotContain("FROM geo_units u\n");
        assertThat(LayerService.buildLayerSql(true)).doesNotContain("FROM geo_units u\n");
    }

    @Test
    void renderLayer_noMatchingRows_returnsEmptyCollection() {
        stubGenerationId("gen-1");

        String result = service.renderLayer(GeoUnitType.DISTRICT, 2, null);

        assertThat(result).isEqualTo(LayerService.EMPTY_FEATURE_COLLECTION);
    }

    @Test
    void getETag_includesScopeAndGenerationId() {
        stubGenerationId("gen-9");

        assertThat(service.getETag(GeoUnitType.PARISH, 3, "1106")).isEqualTo("parish-3-1106-gen-9");
    }

    @Test
    void getETag_noParent_usesAll() {
        stubGenerationId("gen-9");

        assertThat(service.getETag(GeoUnitType.DISTRICT, 2, null)).isEqualTo("district-2-all-gen-9");
    }

    @Test
    void getETag_noGeneration_usesNone() {
        stubGenerationId(null);

        assertThat(service.getETag(GeoUnitType.DISTRICT, 2, null)).isEqualTo("district-2-all-none");
    }
}