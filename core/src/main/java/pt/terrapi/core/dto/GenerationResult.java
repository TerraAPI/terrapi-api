package pt.terrapi.core.dto;

import pt.terrapi.core.enums.GenerationStatus;

import java.util.UUID;

public record GenerationResult(UUID generationId, GenerationStatus status, int rowCount) {}
