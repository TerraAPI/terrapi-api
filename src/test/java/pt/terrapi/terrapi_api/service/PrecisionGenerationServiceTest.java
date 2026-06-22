package pt.terrapi.terrapi_api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pt.terrapi.terrapi_api.dto.GenerationResult;
import pt.terrapi.terrapi_api.entities.PrecisionGeneration;
import pt.terrapi.terrapi_api.enums.GenerationStatus;
import pt.terrapi.terrapi_api.enums.GenerationType;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
import pt.terrapi.terrapi_api.repository.PrecisionGenerationRepository;
import pt.terrapi.terrapi_api.service.PrecisionWriter.WriteResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrecisionGenerationServiceTest {

    @Mock private PrecisionWriter writer;
    @Mock private PrecisionGenerationRepository generationRepository;

    @InjectMocks
    private PrecisionGenerationService service;

    @Test
    void generate_success_returnsSuccessAndSavesAudit() {
        when(writer.write(any(UUID.class), anyList(), isNull()))
                .thenReturn(new WriteResult(10, 0, 0, 5, 2, false));

        GenerationResult result = service.generate(GenerationType.DISTRICT);

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(10);
        verify(generationRepository).save(any(PrecisionGeneration.class));
    }

    @Test
    void generate_degraded_returnsDegraded() {
        when(writer.write(any(UUID.class), anyList(), isNull()))
                .thenReturn(new WriteResult(10, 0, 0, 5, 2, true));

        GenerationResult result = service.generate(GenerationType.PARISH);

        assertThat(result.status()).isEqualTo(GenerationStatus.DEGRADED);
    }

    @Test
    void generate_writerThrows_returnsFailedAndSavesFailedAudit() {
        when(writer.write(any(UUID.class), anyList(), isNull()))
                .thenThrow(new PrecisionWriter.GenerationFailedException("unhealthy"));

        GenerationResult result = service.generate(GenerationType.MUNICIPALITY);

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.rowCount()).isEqualTo(0);

        ArgumentCaptor<PrecisionGeneration> captor = ArgumentCaptor.forClass(PrecisionGeneration.class);
        verify(generationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    void generate_passesLodToWriter() {
        when(writer.write(any(UUID.class), anyList(), eq(2)))
                .thenReturn(new WriteResult(3, 0, 0, 3, 1, false));

        service.generate(GenerationType.PARISH, 2);

        verify(writer).write(any(UUID.class), anyList(), eq(2));
    }

    @Test
    void generate_overload_usesNullLod() {
        when(writer.write(any(UUID.class), anyList(), isNull()))
                .thenReturn(new WriteResult(1, 0, 0, 1, 1, false));

        service.generate(GenerationType.DISTRICT);

        verify(writer).write(any(UUID.class), anyList(), isNull());
    }

    @Test
    void generate_all_resolvesAllTypes() {
        ArgumentCaptor<List<GeoUnitType>> typesCaptor = ArgumentCaptor.forClass(List.class);
        when(writer.write(any(UUID.class), anyList(), isNull()))
                .thenReturn(new WriteResult(21, 0, 0, 21, 7, false));

        service.generate(GenerationType.ALL);

        verify(writer).write(any(UUID.class), typesCaptor.capture(), isNull());
        assertThat(typesCaptor.getValue()).hasSize(GeoUnitType.values().length);
    }
}
