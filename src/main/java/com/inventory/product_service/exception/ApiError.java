package com.inventory.product_service.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape this service returns. One consistent structure for every failure
 * means a client can write one error-handling path instead of one per endpoint.
 *
 * <p>This matters more in a microservice system than in a monolith: from Phase 5 the gateway
 * forwards these bodies straight to browsers, and from Phase 3 order-service parses them
 * programmatically. Errors are part of your API contract, not an afterthought.
 *
 * <p>{@code fieldErrors} is only populated for validation failures, and
 * {@code @JsonInclude(NON_NULL)} keeps it out of the JSON entirely otherwise.
 *
 * <p>Spring 6 ships {@code ProblemDetail}, a built-in implementation of RFC 9457
 * (Problem Details for HTTP APIs), which is what you would likely reach for in production -
 * it is standard, so tooling understands it. We hand-roll the record here because seeing the
 * fields spelled out makes the "exception becomes JSON" translation concrete. Worth
 * revisiting once the mechanism is second nature.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError validation(String path, Map<String, String> fieldErrors) {
        return new ApiError(
                Instant.now(),
                400,
                "Bad Request",
                "Request validation failed",
                path,
                fieldErrors);
    }
}
