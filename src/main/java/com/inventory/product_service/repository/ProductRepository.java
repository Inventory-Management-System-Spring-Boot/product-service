package com.inventory.product_service.repository;

import com.inventory.product_service.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access for {@link Product}.
 *
 * <p>There is no implementation class, and you never write one. At startup Spring Data scans
 * for interfaces extending {@link JpaRepository}, generates a proxy for each, and registers
 * it as a bean. Extending {@code JpaRepository<Product, Long>} already gives you
 * {@code save}, {@code findById}, {@code findAll}, {@code deleteById}, {@code count},
 * paging and sorting.
 *
 * <p>The methods below are <em>derived queries</em>: Spring Data parses the method
 * <em>name</em> and builds the JPQL from it. {@code findBySku} becomes
 * {@code select p from Product p where p.sku = ?1}. Because it is name-driven, a typo like
 * {@code findBySkuu} fails at application startup rather than at runtime - one of the nicer
 * properties of the whole mechanism.
 *
 * <p>Derived names stop being readable somewhere around three conditions. Past that, use
 * {@code @Query} with explicit JPQL, or a Specification for genuinely dynamic filtering.
 *
 * <p>{@code @Repository} is optional here - Spring Data registers the bean regardless - but
 * it documents the role and it is what you will see in most codebases.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * {@link Optional} rather than a bare {@code Product}, so "no such product" is part of
     * the method's signature and the caller cannot forget to handle it. Returning null and
     * hoping is how you get a {@code NullPointerException} three layers up.
     */
    Optional<Product> findBySku(String sku);

    /**
     * Cheaper than {@code findBySku(...).isPresent()} - it issues a {@code COUNT} and never
     * materialises the entity.
     *
     * <p>Careful though: this is a check, not a guarantee. Between this query and the INSERT,
     * another request can create the same SKU. That race is why the UNIQUE constraint exists
     * in the database; this check only exists to produce a friendly error message in the
     * common case. See {@code ProductService.create}.
     */
    boolean existsBySku(String sku);
}
