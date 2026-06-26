package pt.terrapi.pipeline.dto;

import pt.terrapi.pipeline.enums.GenerationStatus;

import java.util.UUID;

public record GenerationResult(UUID generationId, GenerationStatus status, int rowCount) {}
