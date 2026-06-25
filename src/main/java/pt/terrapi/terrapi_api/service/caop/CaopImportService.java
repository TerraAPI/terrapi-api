package pt.terrapi.terrapi_api.service.caop;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pt.terrapi.terrapi_api.dto.GenerationResult;
import pt.terrapi.terrapi_api.dto.ImportResult;
import pt.terrapi.terrapi_api.entities.GeoUnit;
import pt.terrapi.terrapi_api.enums.GenerationStatus;
import pt.terrapi.terrapi_api.enums.GeoUnitType;
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
        // Full rebuild: start from an empty table so that entities split across files
        // (the Azores NUTS levels) merge cleanly and scalar sums never double-count.
        writer.clearAuxData();
        writer.clearGeoUnits();
        Map<GeoUnitType, Set<String>> codesByType = new EnumMap<>(GeoUnitType.class);
        for (File file : files) {
            importFile(file.getAbsolutePath(), codesByType);
        }
        ImportResult result = distinctResult(codesByType);
        log.info("Import finished — {}", result.counts());

        finalizeImport(codesByType);
        return result;
    }

    private void importFile(String filePath, Map<GeoUnitType, Set<String>> codesByType) {
        long t0 = System.currentTimeMillis();
        GpkgData data = reader.read(filePath);
        writer.upsertGeoUnits(data.units(), data.sourceEpsg());
        writer.insertBorderSegments(data.borders(), data.sourceEpsg());
        for (GeoUnit u : data.units()) {
            codesByType.computeIfAbsent(u.getType(), k -> new HashSet<>()).add(u.getCode());
        }
        log.info("  Imported {} units, {} borders in {} ms",
                data.units().size(), data.borders().size(), System.currentTimeMillis() - t0);
    }

    /**
     * Verify (loudly — a thrown check rolls the import back), then derive and trigger precision
     * regeneration. Integrity is checked before derivation so a corrupt import fails fast.
     */
    private void finalizeImport(Map<GeoUnitType, Set<String>> codesByType) {
        verifier.verifyGeometryIntegration();
        verifier.verifyCompleteness(codesByType);
        verifier.verifyReferentialIntegrity();
        deriver.deriveAll();
        triggerGeneration();
    }

    /**
     * Builds the result from distinct persisted codes per type, so entities merged across files
     * (the Azores NUTS) are counted once rather than once per source file.
     */
    private static ImportResult distinctResult(Map<GeoUnitType, Set<String>> codesByType) {
        Map<String, Integer> counts = new HashMap<>();
        codesByType.forEach((type, codes) -> counts.put(type.name(), codes.size()));
        return new ImportResult(counts);
    }

    private void triggerGeneration() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        log.info("Precision generation started (async)");
                        GenerationResult result = precisionGenerationService.generate();
                        if (result.status() != GenerationStatus.SUCCESS) {
                            log.error("Precision generation {} after import did NOT succeed "
                                    + "(status={}). Layer endpoints keep serving the PREVIOUS "
                                    + "precisions until a successful regeneration; check "
                                    + "GET /api/v1/precision/status.",
                                    result.generationId(), result.status());
                        }
                    } catch (Exception e) {
                        log.error("Precision generation failed after import", e);
                    }
                }, taskExecutor);
            }
        });
    }
}
