package com.inventory.product_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into HTTP responses, for every controller in the application.
 *
 * <p>{@code @RestControllerAdvice} registers these handlers globally. When a controller
 * method throws, Spring walks these methods looking for the most specific matching
 * {@code @ExceptionHandler} and uses its return value as the response.
 *
 * <p>The payoff is that controllers and services contain no error-formatting code at all.
 * Without this class, every method would need its own try/catch that builds a
 * {@code ResponseEntity} - the same six lines repeated, drifting out of sync, and eventually
 * leaking a stack trace to a client from the one endpoint somebody forgot.
 *
 * <p>This is the first genuinely <em>cross-cutting concern</em> in the project: one
 * declaration that applies to code it does not know about. Keep the shape in mind - it is the
 * same idea as a gateway filter in Phase 5 and a circuit breaker in Phase 7, applied at a
 * different scope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 404 - the resource genuinely does not exist. */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            ProductNotFoundException ex, HttpServletRequest request) {

        // debug, not error: a 404 is a normal outcome of a client asking for something that
        // is not there. Logging it at ERROR trains everyone to ignore your error logs, which
        // is how the real failures end up invisible.
        log.debug("Product not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    /** 409 - well-formed request, but it conflicts with existing state. */
    @ExceptionHandler(DuplicateSkuException.class)
    public ResponseEntity<ApiError> handleDuplicateSku(
            DuplicateSkuException ex, HttpServletRequest request) {

        log.debug("Duplicate SKU rejected: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", ex.getMessage(), request.getRequestURI()));
    }

    /**
     * 400 with per-field detail - thrown by {@code @Valid} when Bean Validation fails.
     *
     * <p>Returning which field failed and why is the difference between an API a client can
     * correct against and one they have to guess at. Spring's default response here is a
     * wall of unusable detail, so overriding it is nearly always worth the few lines.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        // LinkedHashMap to keep field order stable, which makes responses (and the tests
        // asserting on them) predictable.
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                // merge, not put: one field can violate several constraints at once, and
                // silently dropping all but the last is a confusing API.
                fieldErrors.merge(
                        error.getField(),
                        error.getDefaultMessage(),
                        (existing, next) -> existing + "; " + next));

        log.debug("Validation failed for {}: {}", request.getRequestURI(), fieldErrors);

        return ResponseEntity.badRequest()
                .body(ApiError.validation(request.getRequestURI(), fieldErrors));
    }

    /**
     * 409 - the database rejected the write.
     *
     * <p>This is the safety net behind {@code ProductService}'s {@code existsBySku} check.
     * That check loses to a race: two concurrent requests can both see "SKU free" before
     * either inserts, and then the UNIQUE constraint rejects the second one. The pre-check
     * exists for a clear error message in the common case; <em>this</em> handler is what
     * makes the guarantee actually hold.
     *
     * <p>Note that we cannot catch this inside the service and carry on, because by the time
     * the constraint fires the transaction is already marked rollback-only - any further
     * query on it throws. So the exception has to propagate out to here, past the transaction
     * boundary. That constraint on where you can recover from a database error catches almost
     * everyone once.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        // WARN with the exception: usually a lost race (fine), occasionally a real bug in a
        // constraint we forgot about (not fine). Worth being able to find later.
        log.warn("Database constraint violated on {}", request.getRequestURI(), ex);

        // Deliberately vague. The underlying message contains table names, column names and
        // constraint names - internal schema details that no client should be handed.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict",
                        "The request conflicts with existing data",
                        request.getRequestURI()));
    }

    /** 400 - malformed JSON, or a value Jackson could not parse into the target type. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.debug("Unreadable request body on {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request",
                        "Malformed request body - expected valid JSON",
                        request.getRequestURI()));
    }

    /** 400 - e.g. {@code GET /api/products/abc} where a Long id was expected. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String message = "Parameter '%s' has an invalid value: '%s'"
                .formatted(ex.getName(), ex.getValue());

        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", message, request.getRequestURI()));
    }

    /**
     * 500 - the catch-all for anything we did not anticipate.
     *
     * <p>Two rules, both important. Log the full stack trace, because this is the only record
     * you will have of a genuine bug. And return a generic message: stack traces and
     * exception class names in a response body are an information-disclosure vulnerability,
     * telling an attacker your framework versions and internal structure.
     *
     * <p>From Phase 9 the trace ID goes in this response, so a user can quote an id and you
     * can find the exact request across every service. That is the piece that makes
     * "something went wrong" an acceptable thing to say to a user.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception on {} {}",
                request.getMethod(), request.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error",
                        "An unexpected error occurred",
                        request.getRequestURI()));
    }
}
