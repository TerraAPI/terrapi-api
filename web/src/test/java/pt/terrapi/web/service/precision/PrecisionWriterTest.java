package pt.terrapi.web.service.precision;

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
import pt.terrapi.core.config.LodLevel;
import pt.terrapi.core.config.PrecisionProperties;
import pt.terrapi.web.service.precision.PrecisionWriter.ValidationResult;
import pt.terrapi.web.service.precision.PrecisionWriter.WriteResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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

        when(jdbcTemplate.update(anyString())).thenReturn(0);
        when(jdbcTemplate.update(anyString(), anyInt())).thenReturn(0);
        when(jdbcTemplate.update(anyString(), anyInt(), anyDouble(), any(UUID.class))).thenReturn(5);
        when(jdbcTemplate.update(anyString(), anyInt(), anyDouble(), any(UUID.class), anyDouble()))
                .thenReturn(9);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(3259);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(100L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyInt())).thenReturn(100L);
    }

    private void stubLadder(LodLevel... levels) {
        when(policyService.getLodLadder()).thenReturn(List.of(levels));
    }

    private void stubValidation(int total, int nulls, int invalid, int empty) {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(new ValidationResult(total, nulls, invalid, empty));
    }

    @Test
    void write_simplifiesParishBaseAndReportsValidationRowCount() {
        stubLadder(new LodLevel(0, 25.0));
        stubValidation(100, 0, 0, 0);

        WriteResult result = writer.write(UUID.randomUUID(), null);

        assertThat(result.rowCount()).isEqualTo(100);

        ArgumentCaptor<String> exec = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).execute(exec.capture());
        assertThat(exec.getAllValues()).anyMatch(s -> s.contains("ST_CoverageSimplify"));
    }

    @Test
    void write_unhealthy_throws() {
        stubLadder(new LodLevel(0, 25.0));
        stubValidation(100, 0, 60, 0);

        assertThatThrownBy(() -> writer.write(UUID.randomUUID(), null))
                .isInstanceOf(PrecisionWriter.GenerationFailedException.class);
    }

    @Test
    void write_withLod_deletesOnlyThatLod() {
        stubLadder(new LodLevel(0, 25.0), new LodLevel(2, 200.0));
        stubValidation(50, 0, 0, 0);

        writer.write(UUID.randomUUID(), 2);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(sql.capture(), anyInt());
        assertThat(sql.getAllValues()).anyMatch(s -> s.contains("WHERE lod = ?"));
    }

    @Test
    void write_emptyLadder_returnsEmpty() {
        when(policyService.getLodLadder()).thenReturn(List.of());

        WriteResult result = writer.write(UUID.randomUUID(), null);

        assertThat(result.rowCount()).isZero();
        assertThat(result.totalLods()).isZero();
    }
}