package com.inventory.product_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The outbound half of the API contract: exactly what this service promises to return.
 *
 * <p>Being a separate type from {@link ProductRequest} is the point. Requests and responses
 * evolve for different reasons - a response can safely gain a field, while a request gaining
 * a required field breaks every existing client - and asymmetric fields like
 * {@code createdAt} belong in only one of them.
 *
 * <p>Treat this class as a published API. From Phase 3, order-service deserializes this
 * shape; from Phase 5 it is what the gateway forwards to browsers. Renaming a field here is
 * a breaking change for code you do not control, and the compiler will not warn you. That
 * is why the entity is kept behind it: {@code products.name} can be renamed freely as long
 * as the mapper still fills in {@code name}.
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Instant createdAt,
        Instant updatedAt
) {
}
