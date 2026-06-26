package pt.terrapi.core.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Surfaces failures to API callers (admin tooling) with a clear status and the failure message in
 * the response body, instead of a bare 500 whose detail only reaches the server logs.
 *
 * <ul>
 *   <li>{@link IllegalArgumentException} — bad input (missing/too many files, bad path) → 400.</li>
 *   <li>{@link MaxUploadSizeExceededException} — upload over the configured limit → 413.</li>
 *   <li>{@link IllegalStateException} — import/tool failure (e.g. the osm2pgsql tail) → 500.</li>
 * </ul>
 *
 * <p>Deliberately scoped to these types so Spring's own request-binding exceptions (type
 * mismatch, missing params, etc.) keep their default 4xx mapping on the read endpoints.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(int status, String error, String message) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException e) {
        log.debug("Bad request: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
        log.warn("Upload rejected — over size limit: {}", e.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the configured size limit.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleServerFailure(IllegalStateException e) {
        log.error("Request failed", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message));
    }
}
