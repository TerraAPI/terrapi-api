package pt.terrapi.core.dto;

import pt.terrapi.terrapi_api.enums.GenerationStatus;

import java.util.UUID;

public record GenerationResult(UUID generationId, GenerationStatus status, int rowCount) {}
