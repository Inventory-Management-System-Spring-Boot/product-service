package com.inventory.product_service.exception;

/**
 * Thrown when creating a product whose SKU already exists. Mapped to HTTP 409 Conflict.
 *
 * <p>409 rather than 400: the request is perfectly well-formed, it just conflicts with the
 * current state of the resource. Retrying the identical request will not help until something
 * changes on the server. That distinction is what tells a client whether to fix its payload
 * (400) or give up (409) - and it is the sort of detail that separates an API people can
 * automate against from one they have to guess at.
 */
public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("A product with sku '" + sku + "' already exists");
    }
}
