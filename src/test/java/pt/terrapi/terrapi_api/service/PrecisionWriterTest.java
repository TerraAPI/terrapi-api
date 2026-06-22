package pt.terrapi.terrapi_api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pt.terrapi.terrapi_api.config.LodLevel;
import pt.terrapi.terrapi_api.config.PrecisionProperties;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.service.PrecisionWriter.ValidationResult;
import pt.terrapi.terrapi_api.service.PrecisionWriter.WriteResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrecisionWriterTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private PrecisionPolicyService policyService;
    @Mock private PrecisionProperties properties;

    @InjectMocks
    private PrecisionWriter writer;

    private PrecisionProperties.Validation validation;

    @BeforeEach
    void setUp() {
        validation = new PrecisionProperties.Validation();
        validation.setMaxNullPct(25.0);
        validation.setMaxInvalidPct(25.0);
        validation.setMaxEmptyPct(25.0);
        when(properties.getValidation()).thenReturn(validation);

        when(jdbcTemplate.update(anyString(), anyInt())).thenReturn(0);
        when(jdbcTemplate.update(anyString(), anyInt(), anyInt())).thenReturn(0);
        when(jdbcTemplate.update(anyString(), anyInt(), anyInt(), any(UUID.class))).thenReturn(10);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyInt())).thenReturn(5L);
    }

    private void stubValidation(int total, int nulls, int invalid, int empty) {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(UUID.class)))
                .thenReturn(new ValidationResult(total, nulls, invalid, empty));
    }

    private void stubCoverageValidity(int invalidEdges) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyDouble(), anyInt()))
                .thenReturn(invalidEdges);
    }

    @Test
    void write_healthy_perFeature_returnsResult() {
        when(policyService.getLodLevels(GeoUnitType.DISTRICT))
                .thenReturn(List.of(new LodLevel(1, 100.0), new LodLevel(2, 200.0)));
        when(policyService.isTopologyPreserving(GeoUnitType.DISTRICT)).thenReturn(false);
        stubValidation(10, 0, 0, 0);

        WriteResult result = writer.write(UUID.randomUUID(), List.of(GeoUnitType.DISTRICT), null);

        assertThat(result.rowCount()).isEqualTo(10);
        assertThat(result.totalUnits()).isEqualTo(5);
        assertThat(result.totalLods()).isEqualTo(2);
        assertThat(result.degraded()).isFalse();

        ArgumentCaptor<String> deleteSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(deleteSql.capture(), anyInt());
        assertThat(deleteSql.getValue()).contains("DELETE FROM geo_unit_precisions").doesNotContain("lod");
    }

    @Test
    void write_unhealthy_throwsAndRollsBack() {
        when(policyService.getLodLevels(GeoUnitType.MUNICIPALITY))
                .thenReturn(List.of(new LodLevel(1, 100.0)));
        when(policyService.isTopologyPreserving(GeoUnitType.MUNICIPALITY)).thenReturn(false);
        stubValidation(10, 0, 5, 0);

        assertThatThrownBy(() ->
                writer.write(UUID.randomUUID(), List.of(GeoUnitType.MUNICIPALITY), null))
                .isInstanceOf(PrecisionWriter.GenerationFailedException.class);
    }

    @Test
    void write_withLod_filtersLevelsAndScopesDelete() {
        when(policyService.getLodLevels(GeoUnitType.PARISH))
                .thenReturn(List.of(new LodLevel(0, 25.0), new LodLevel(1, 50.0), new LodLevel(2, 200.0)));
        when(policyService.isTopologyPreserving(GeoUnitType.PARISH)).thenReturn(false);
        stubValidation(5, 0, 0, 0);

        WriteResult result = writer.write(UUID.randomUUID(), List.of(GeoUnitType.PARISH), 1);

        assertThat(result.totalLods()).isEqualTo(1);

        ArgumentCaptor<String> deleteSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(deleteSql.capture(), anyInt(), anyInt());
        assertThat(deleteSql.getValue()).contains("AND lod = ?");
    }

    @Test
    void write_validCoverage_usesCoverageExpression() {
        when(policyService.getLodLevels(GeoUnitType.DISTRICT))
                .thenReturn(List.of(new LodLevel(1, 100.0)));
        when(policyService.isTopologyPreserving(GeoUnitType.DISTRICT)).thenReturn(true);
        when(policyService.isSimplifyBoundary()).thenReturn(false);
        stubCoverageValidity(0);
        stubValidation(5, 0, 0, 0);

        WriteResult result = writer.write(UUID.randomUUID(), List.of(GeoUnitType.DISTRICT), null);

        assertThat(result.degraded()).isFalse();

        ArgumentCaptor<String> insertSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(insertSql.capture(), anyInt(), anyInt(), any(UUID.class));
        assertThat(insertSql.getValue()).contains("ST_CoverageSimplify");
    }

    @Test
    void write_invalidCoverage_fallsBackToPerFeatureAndDegraded() {
        when(policyService.getLodLevels(GeoUnitType.PARISH))
                .thenReturn(List.of(new LodLevel(1, 50.0)));
        when(policyService.isTopologyPreserving(GeoUnitType.PARISH)).thenReturn(true);
        stubCoverageValidity(7);
        stubValidation(5, 0, 0, 0);

        WriteResult result = writer.write(UUID.randomUUID(), List.of(GeoUnitType.PARISH), null);

        assertThat(result.degraded()).isTrue();

        ArgumentCaptor<String> insertSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(insertSql.capture(), anyInt(), anyInt(), any(UUID.class));
        assertThat(insertSql.getValue()).contains("ST_SimplifyPreserveTopology");
    }

    @Test
    void write_noLevels_skipsTypeAndIsHealthy() {
        when(policyService.getLodLevels(GeoUnitType.DISTRICT)).thenReturn(List.of());
        stubValidation(0, 0, 0, 0);

        WriteResult result = writer.write(UUID.randomUUID(), List.of(GeoUnitType.DISTRICT), null);

        assertThat(result.rowCount()).isZero();
        assertThat(result.totalLods()).isZero();
    }
}
