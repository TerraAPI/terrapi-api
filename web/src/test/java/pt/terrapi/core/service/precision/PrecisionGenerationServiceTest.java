package pt.terrapi.core.service.precision;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pt.terrapi.core.dto.GenerationResult;
import pt.terrapi.core.entities.PrecisionGeneration;
import pt.terrapi.core.enums.GenerationStatus;
import pt.terrapi.core.repository.PrecisionGenerationRepository;
import pt.terrapi.core.service.precision.PrecisionWriter.WriteResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrecisionGenerationServiceTest {

    @Mock private PrecisionWriter writer;
    @Mock private PrecisionGenerationRepository generationRepository;

    @InjectMocks
    private PrecisionGenerationService service;

    @BeforeEach
    void stubIdGeneration() {
        when(generationRepository.save(any(PrecisionGeneration.class))).thenAnswer(inv -> {
            PrecisionGeneration gen = inv.getArgument(0);
            if (gen.getGenerationId() == null) {
                gen.setGenerationId(UUID.randomUUID());
            }
            return gen;
        });
    }

    @Test
    void generate_success_returnsSuccessAndSavesAudit() {
        when(writer.write(any(UUID.class), isNull()))
                .thenReturn(new WriteResult(10, 0, 0, 5, 2));

        GenerationResult result = service.generate();

        assertThat(result.status()).isEqualTo(GenerationStatus.SUCCESS);
        assertThat(result.rowCount()).isEqualTo(10);
        verify(generationRepository, times(2)).save(any(PrecisionGeneration.class));
    }

    @Test
    void generate_writerThrows_returnsFailedAndSavesFailedAudit() {
        when(writer.write(any(UUID.class), isNull()))
                .thenThrow(new PrecisionWriter.GenerationFailedException("unhealthy"));

        GenerationResult result = service.generate();

        assertThat(result.status()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.rowCount()).isEqualTo(0);

        ArgumentCaptor<PrecisionGeneration> captor = ArgumentCaptor.forClass(PrecisionGeneration.class);
        verify(generationRepository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    void generate_passesLodToWriter() {
        when(writer.write(any(UUID.class), eq(2)))
                .thenReturn(new WriteResult(3, 0, 0, 3, 1));

        service.generate(2);

        verify(writer).write(any(UUID.class), eq(2));
    }

    @Test
    void generate_overload_usesNullLod() {
        when(writer.write(any(UUID.class), isNull()))
                .thenReturn(new WriteResult(1, 0, 0, 1, 1));

        service.generate();

        verify(writer).write(any(UUID.class), isNull());
    }
}