package pt.terrapi.pipeline.precision;

import pt.terrapi.pipeline.precision.GenerationStatus;

import java.util.UUID;

public record GenerationResult(UUID generationId, GenerationStatus status, int rowCount) {}
