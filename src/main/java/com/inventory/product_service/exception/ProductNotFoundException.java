package com.inventory.product_service.exception;

/**
 * Thrown when a product does not exist. Mapped to HTTP 404 by
 * {@link GlobalExceptionHandler}.
 *
 * <p>Why a custom exception instead of returning null, or an {@code Optional}, or building a
 * {@code ResponseEntity.notFound()} in the service? Because the service layer must not know
 * that HTTP exists. Today product-service is called over REST; in Phase 8 the same service
 * methods get called from a RabbitMQ listener where "404" is meaningless. Throwing a
 * domain exception and translating it to a protocol at the edge keeps the business logic
 * reusable - and that separation is exactly what lets a service be driven by more than one
 * kind of input.
 *
 * <p>Extends {@link RuntimeException}, not {@code Exception}. A checked exception would force
 * every caller up the stack to declare it, and there is nothing a controller can meaningfully
 * <em>do</em> about a missing row except report it. Spring's own data access exceptions are
 * unchecked for the same reason.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String message) {
        super(message);
    }

    public static ProductNotFoundException byId(Long id) {
        return new ProductNotFoundException("No product found with id " + id);
    }

    public static ProductNotFoundException bySku(String sku) {
        return new ProductNotFoundException("No product found with sku '" + sku + "'");
    }
}
