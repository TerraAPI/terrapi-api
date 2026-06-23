package pt.terrapi.terrapi_api.service.caop;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pt.terrapi.terrapi_api.dto.ImportResult;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.service.caop.CaopGpkgReader.GpkgData;
import pt.terrapi.terrapi_api.service.precision.PrecisionGenerationService;

/**
 * Orchestrates a CAOP import: read each GeoPackage ({@link CaopGpkgReader}) → write to PostGIS
 * ({@link GeoUnitWriter}) → verify ({@link ImportVerifier}) → derive ({@link GeoDerivationService})
 * → asynchronously regenerate precisions.
 */
@Slf4j
@Service
public class CaopImportService {

    private final CaopGpkgReader reader;
    private final GeoUnitWriter writer;
    private final GeoDerivationService deriver;
    private final ImportVerifier verifier;
    private final PrecisionGenerationService precisionGenerationService;
    private final TaskExecutor taskExecutor;

    public CaopImportService(CaopGpkgReader reader,
                             GeoUnitWriter writer,
                             GeoDerivationService deriver,
                             ImportVerifier verifier,
                             PrecisionGenerationService precisionGenerationService,
                             @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.reader = reader;
        this.writer = writer;
        this.deriver = deriver;
        this.verifier = verifier;
        this.precisionGenerationService = precisionGenerationService;
        this.taskExecutor = taskExecutor;
    }

    @Transactional
    public ImportResult importFolder(String folderPath) {
        File folder = new File(folderPath);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".gpkg"));
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No .gpkg files found in " + folderPath);
        }

        log.info("Importing {} .gpkg files from {}", files.length, folderPath);
        writer.clearAuxData();
        ImportResult total = ImportResult.empty();
        for (File file : files) {
            total = total.add(importFile(file.getAbsolutePath()));
        }
        log.info("Import finished — {}", total.counts());

        finalizeImport();
        return total;
    }

    @Transactional
    public ImportResult importGpkg(String filePath) {
        writer.clearAuxData();
        ImportResult result = importFile(filePath);
        log.info("Import finished — {}", result.counts());

        finalizeImport();
        return result;
    }

    private ImportResult importFile(String filePath) {
        long t0 = System.currentTimeMillis();
        GpkgData data = reader.read(filePath);
        writer.upsertGeoUnits(data.units(), data.sourceEpsg());
        writer.insertBorderSegments(data.borders(), data.sourceEpsg());
        ImportResult result = countByType(data);
        log.info("  Imported {} units, {} borders in {} ms",
                result.total(), data.borders().size(), System.currentTimeMillis() - t0);
        return result;
    }

    private void finalizeImport() {
        verifier.verifyGeometryIntegration();
        deriver.deriveAll();
        triggerGeneration();
    }

    private static ImportResult countByType(GpkgData data) {
        Map<String, Integer> counts = new HashMap<>();
        for (GeoUnit unit : data.units()) {
            counts.merge(unit.getType().name(), 1, Integer::sum);
        }
        return new ImportResult(counts);
    }

    private void triggerGeneration() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        log.info("Precision generation started (async)");
                        precisionGenerationService.generate();
                    } catch (Exception e) {
                        log.error("Precision generation failed after import", e);
                    }
                }, taskExecutor);
            }
        });
    }
}
