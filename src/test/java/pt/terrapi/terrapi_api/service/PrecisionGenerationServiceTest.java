package pt.terrapi.terrapi_api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Geometry;
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
import pt.terrapi.terrapi_api.entities.GeoUnitPrecision;
import pt.terrapi.terrapi_api.entities.PrecisionGeneration;
import pt.terrapi.terrapi_api.enums.GenerationStatus;
import pt.terrapi.terrapi_api.enums.GenerationType;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.repository.GeoUnitPrecisionRepository;
import pt.terrapi.terrapi_api.repository.PrecisionGenerationRepository;

import java.util.ArrayList;
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

    @Test
    void generate_singleType_success() {
        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.DISTRICT)).thenReturn(List.of(lod));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(5L);

        List<GeoUnitPrecision> precisions = buildPrecisions(5, true, false);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(precisions);

        GenerationResult result = service.generate(GenerationType.DISTRICT);

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(5);

        verify(precisionRepository).deprecateActiveByTypes(List.of(GeoUnitType.DISTRICT));
        verify(precisionRepository).saveAll(precisions);
        verify(generationRepository).save(any(PrecisionGeneration.class));
    }

    @Test
    void generate_tooManyInvalid_returnsFailed() {
        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.MUNICIPALITY)).thenReturn(List.of(lod));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(10L);

        List<GeoUnitPrecision> precisions = buildPrecisions(10, false, false);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(precisions);

        GenerationResult result = service.generate(GenerationType.MUNICIPALITY);

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.rowCount()).isEqualTo(10);

        verify(precisionRepository, never()).saveAll(any());
    }

    @Test
    void generate_nullGeometries_returnsFailed() {
        validation.setMaxNullPct(0.0);

        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.PARISH)).thenReturn(List.of(lod));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(5L);

        List<GeoUnitPrecision> precisions = buildPrecisions(3, true, false);
        precisions.addAll(buildNullPrecisions(2));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(precisions);

        GenerationResult result = service.generate(GenerationType.PARISH);

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.rowCount()).isEqualTo(5);
    }

    @Test
    void generate_emptyGeometries_returnsFailed() {
        validation.setMaxEmptyPct(0.0);

        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.ISLAND)).thenReturn(List.of(lod));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(5L);

        List<GeoUnitPrecision> precisions = buildPrecisions(5, true, true);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(precisions);

        GenerationResult result = service.generate(GenerationType.ISLAND);

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    void generate_rowCountMismatch_returnsFailed() {
        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.NUTS1)).thenReturn(List.of(lod));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(10L);

        List<GeoUnitPrecision> precisions = buildPrecisions(7, true, false);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(precisions);

        GenerationResult result = service.generate(GenerationType.NUTS1);

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.rowCount()).isEqualTo(7);
    }

    @Test
    void generate_jdbcThrows_returnsFailed() {
        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.DISTRICT)).thenReturn(List.of(lod));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(5L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenThrow(new RuntimeException("SQL error"));

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
        }
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(3L);

        List<GeoUnitPrecision> precisions = buildPrecisions(3, true, false);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(precisions);

        GenerationResult result = service.generate(GenerationType.ALL);

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(21);

        verify(precisionRepository).deprecateActiveByTypes(List.of(GeoUnitType.values()));
    }

    @Test
    void generate_mixedValidAndInvalid_withinThreshold_success() {
        LodLevel lod = new LodLevel(1, 100.0);
        when(policyService.getLodLevels(GeoUnitType.DISTRICT)).thenReturn(List.of(lod));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(8L);

        List<GeoUnitPrecision> precisions = buildPrecisions(6, true, false);
        precisions.addAll(buildPrecisions(2, false, false));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(precisions);

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
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
                .thenReturn(1L);

        List<GeoUnitPrecision> precisions = buildPrecisions(1, true, false);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(precisions);

        GenerationResult result = service.generate(GenerationType.DISTRICT);

        assertThat(result.generationId()).isNotNull();
        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCESS);
    }

    private static List<GeoUnitPrecision> buildPrecisions(int count, boolean valid, boolean empty) {
        List<GeoUnitPrecision> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            GeoUnitPrecision p = new GeoUnitPrecision();
            p.setGeoUnitCode(String.format("%06d", i));
            p.setType(GeoUnitType.DISTRICT);
            p.setLod(1);
            p.setToleranceM(100.0);
            p.setVertexCount(50);

            Geometry geom = org.mockito.Mockito.mock(Geometry.class);
            org.mockito.Mockito.when(geom.isValid()).thenReturn(valid);
            org.mockito.Mockito.when(geom.isEmpty()).thenReturn(empty);
            p.setGeometry(geom);

            list.add(p);
        }
        return list;
    }

    private static List<GeoUnitPrecision> buildNullPrecisions(int count) {
        List<GeoUnitPrecision> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            GeoUnitPrecision p = new GeoUnitPrecision();
            p.setGeoUnitCode(String.format("%06d", i));
            p.setGeometry(null);
            list.add(p);
        }
        return list;
    }
}
