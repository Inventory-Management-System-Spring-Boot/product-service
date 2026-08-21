package com.inventory.product_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * The inbound half of the API contract: what a client may send to create or update a product.
 *
 * <p>A {@code record} is a good fit - it is immutable, and Jackson can bind JSON straight
 * into the canonical constructor. Note what is <em>not</em> here: no {@code id}, no
 * {@code createdAt}. Those are ours to assign. Accepting an id from a client would let it
 * choose which row it overwrites, which is a genuine vulnerability class
 * (mass assignment). Keeping request and response DTOs separate makes that impossible
 * by construction rather than by remembering to check.
 *
 * <p>The annotations are Bean Validation constraints. They are inert until something asks for
 * them - that something is {@code @Valid} on the controller parameter. Forgetting
 * {@code @Valid} is the single most common reason "my validation isn't working".
 */
public record ProductRequest(

        /*
         * Two separate constraints because they produce two different messages. @NotBlank
         * catches null, "" and "   "; @Pattern then constrains the shape. If you only wrote
         * @Pattern, a null value would pass - Bean Validation treats null as valid for most
         * constraints, on the theory that nullability is @NotNull's job. That asymmetry
         * surprises nearly everyone once.
         */
        @NotBlank(message = "sku is required")
        @Size(max = 64, message = "sku must be at most 64 characters")
        @Pattern(
                regexp = "^[A-Z0-9][A-Z0-9-]*$",
                message = "sku must be uppercase letters, digits and hyphens (e.g. IPHONE-15)")
        String sku,

        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        /* Optional: no @NotBlank. A product legitimately may not have a description. */
        @Size(max = 5000, message = "description must be at most 5000 characters")
        String description,

        /*
         * @NotNull, not @NotBlank - @NotBlank only applies to CharSequence.
         *
         * @Digits mirrors the NUMERIC(12,2) column: without it, a client could send a price
         * with 6 decimal places, and the value silently rounds on insert. Validation that
         * matches your schema turns a silent data change into a clear 400.
         */
        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", message = "price cannot be negative")
        @DecimalMax(value = "9999999999.99", message = "price is too large")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 2 decimal places")
        BigDecimal price
) {
}
