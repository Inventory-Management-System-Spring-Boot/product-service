package com.inventory.product_service.mapper;

import com.inventory.product_service.dto.ProductRequest;
import com.inventory.product_service.dto.ProductResponse;
import com.inventory.product_service.entity.Product;
import org.springframework.stereotype.Component;

/**
 * Translates between the persistence model ({@link Product}) and the API model
 * ({@link ProductRequest} / {@link ProductResponse}).
 *
 * <p>Hand-written on purpose. MapStruct would generate this at compile time and is what most
 * production codebases reach for, but while you are learning, an explicit mapper makes the
 * entity/DTO boundary something you can actually see and step through in a debugger.
 * Generated mappers hide exactly the thing worth understanding here.
 *
 * <p>A {@code @Component} (rather than static methods) so it can be injected. That keeps
 * the door open for a mapper that needs its own dependencies later - a currency converter, a
 * URL builder - without changing every call site.
 */
@Component
public class ProductMapper {

    /**
     * Request to a brand-new entity.
     *
     * <p>{@code id} is left null deliberately: null id is how Hibernate distinguishes a
     * transient object (INSERT) from a detached one (UPDATE). Timestamps are left null too -
     * {@code @PrePersist} fills them in, so there is exactly one place that decides what
     * "now" means.
     */
    public Product toEntity(ProductRequest request) {
        return Product.builder()
                .sku(request.sku())
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .build();
    }

    /**
     * Copies mutable fields from a request onto an existing, managed entity.
     *
     * <p>Note that {@code sku} is not copied. It is the key other services hold, so it is
     * immutable after creation - the entity marks the column {@code updatable = false}, and
     * this method simply never tries.
     *
     * <p>There is no {@code save()} call here, and none is needed. The entity passed in is
     * <em>managed</em> by the persistence context, so Hibernate's dirty checking detects the
     * changed fields and issues an UPDATE when the transaction commits. Understanding that -
     * that mutating a managed entity inside a transaction <em>is</em> the write - is one of
     * the real "oh, that's how JPA works" moments.
     */
    public void updateEntity(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
    }

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
