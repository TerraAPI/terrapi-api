package pt.terrapi.terrapi_api.service;

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
import pt.terrapi.terrapi_api.config.LodLevel;
import pt.terrapi.terrapi_api.config.PrecisionProperties;
import pt.terrapi.terrapi_api.dto.GenerationResult;
import pt.terrapi.terrapi_api.entities.PrecisionGeneration;
import pt.terrapi.terrapi_api.enums.GenerationStatus;
import pt.terrapi.terrapi_api.enums.GenerationType;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.repository.GeoUnitPrecisionRepository;
import pt.terrapi.terrapi_api.repository.PrecisionGenerationRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrecisionGenerationServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private PrecisionPolicyService policyService;
    @Mock private PrecisionGenerationRepository generationRepository;
    @Mock private GeoUnitPrecisionRepository precisionRepository;
    @Mock private PrecisionProperties properties;

    @InjectMocks
    private PrecisionGenerationService service;

    private PrecisionProperties.Validation validation;

    @BeforeEach
    void setUp() {
        validation = new PrecisionProperties.Validation();
        validation.setMaxNullPct(25.0);
        validation.setMaxInvalidPct(25.0);
        validation.setMaxEmptyPct(25.0);
        when(properties.getValidation()).thenReturn(validation);
    }

    private void stubCount(GeoUnitType type, long count) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(type.getValue())))
                .thenReturn(count);
    }

    private void stubInsertLods(int rowCount) {
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(rowCount);
    }

    private void stubInsertLodsThrows(RuntimeException e) {
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenThrow(e);
    }

    private void stubValidation(int totalRows, int nullCount, int invalidCount, int emptyCount) {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any()))
                .thenReturn(new PrecisionGenerationService.ValidationResult(
                        totalRows, nullCount, invalidCount, emptyCount));
    }

    @Test
    void generate_singleType_success() {
        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.DISTRICT)).thenReturn(List.of(lod));
        stubCount(GeoUnitType.DISTRICT, 5);
        stubInsertLods(5);
        stubValidation(5, 0, 0, 0);

        GenerationResult result = service.generate(GenerationType.DISTRICT);

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(5);
        verify(precisionRepository).deprecateActiveByTypes(List.of(GeoUnitType.DISTRICT));
        verify(generationRepository).save(any(PrecisionGeneration.class));
    }

    @Test
    void generate_tooManyInvalid_returnsFailed() {
        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.MUNICIPALITY)).thenReturn(List.of(lod));
        stubCount(GeoUnitType.MUNICIPALITY, 10);
        stubInsertLods(10);
        stubValidation(10, 0, 3, 0);

        GenerationResult result = service.generate(GenerationType.MUNICIPALITY);

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.rowCount()).isEqualTo(10);
    }

    @Test
    void generate_nullGeometries_returnsFailed() {
        validation.setMaxNullPct(0.0);

        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.PARISH)).thenReturn(List.of(lod));
        stubCount(GeoUnitType.PARISH, 5);
        stubInsertLods(5);
        stubValidation(5, 2, 0, 0);

        GenerationResult result = service.generate(GenerationType.PARISH);

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.rowCount()).isEqualTo(5);
    }

    @Test
    void generate_emptyGeometries_returnsFailed() {
        validation.setMaxEmptyPct(0.0);

        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.ISLAND)).thenReturn(List.of(lod));
        stubCount(GeoUnitType.ISLAND, 5);
        stubInsertLods(5);
        stubValidation(5, 0, 0, 5);

        GenerationResult result = service.generate(GenerationType.ISLAND);

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    void generate_rowCountMismatch_returnsFailed() {
        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.NUTS1)).thenReturn(List.of(lod));
        stubCount(GeoUnitType.NUTS1, 10);
        stubInsertLods(7);
        stubValidation(7, 0, 0, 0);

        GenerationResult result = service.generate(GenerationType.NUTS1);

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.rowCount()).isEqualTo(7);
    }

    @Test
    void generate_jdbcThrows_returnsFailed() {
        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.DISTRICT)).thenReturn(List.of(lod));
        stubCount(GeoUnitType.DISTRICT, 5);
        stubInsertLodsThrows(new RuntimeException("SQL error"));

        GenerationResult result = service.generate(GenerationType.DISTRICT);

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.rowCount()).isEqualTo(0);
        verify(generationRepository).save(any(PrecisionGeneration.class));
    }

    @Test
    void generate_noLodLevels_skipsType() {
        when(policyService.getLodLevels(GeoUnitType.DISTRICT)).thenReturn(List.of());

        GenerationResult result = service.generate(GenerationType.DISTRICT);

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(0);
    }

    @Test
    void generate_all_success() {
        List<LodLevel> lods = List.of(new LodLevel(1, 100.0));
        for (GeoUnitType type : GeoUnitType.values()) {
            when(policyService.getLodLevels(type)).thenReturn(lods);
            stubCount(type, 3);
        }
        stubInsertLods(3);
        stubValidation(21, 0, 0, 0);

        GenerationResult result = service.generate(GenerationType.ALL);

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(21);
        verify(precisionRepository).deprecateActiveByTypes(List.of(GeoUnitType.values()));
    }

    @Test
    void generate_mixedValidAndInvalid_withinThreshold_success() {
        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.DISTRICT)).thenReturn(List.of(lod));
        stubCount(GeoUnitType.DISTRICT, 8);
        stubInsertLods(8);
        stubValidation(8, 0, 2, 0);

        GenerationResult result = service.generate(GenerationType.DISTRICT);

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(8);
    }

    @Test
    void updateCounters_setsAllFields() {
        PrecisionGeneration gen = new PrecisionGeneration();
        gen.updateCounters(100, 2, 5, 50, 3);

        assertThat(gen.getRowCount()).isEqualTo(100);
        assertThat(gen.getNullGeometries()).isEqualTo(2);
        assertThat(gen.getInvalidGeometries()).isEqualTo(5);
        assertThat(gen.getTotalUnits()).isEqualTo(50);
        assertThat(gen.getTotalLods()).isEqualTo(3);
    }

    @Test
    void generationResult_recordsGenerationId() {
        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.DISTRICT)).thenReturn(List.of(lod));
        stubCount(GeoUnitType.DISTRICT, 1);
        stubInsertLods(1);
        stubValidation(1, 0, 0, 0);

        GenerationResult result = service.generate(GenerationType.DISTRICT);

        assertThat(result.generationId()).isNotNull();
        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCESS);
    }
}
