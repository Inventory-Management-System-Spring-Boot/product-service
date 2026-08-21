package com.inventory.product_service.service;

import com.inventory.product_service.dto.ProductRequest;
import com.inventory.product_service.dto.ProductResponse;
import com.inventory.product_service.entity.Product;
import com.inventory.product_service.exception.DuplicateSkuException;
import com.inventory.product_service.exception.ProductNotFoundException;
import com.inventory.product_service.mapper.ProductMapper;
import com.inventory.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for products, and the transaction boundary for this service.
 *
 * <p>Notice what this class does <em>not</em> import: nothing from {@code jakarta.servlet},
 * no {@code ResponseEntity}, no {@code HttpStatus}. It has no idea it is being called over
 * HTTP. That is not fastidiousness - in Phase 8 these same methods get invoked from a
 * RabbitMQ listener, and a service that had baked HTTP into its signatures would have to be
 * rewritten to get there.
 *
 * <p>The layering rule in one line: <strong>controllers translate protocols, services make
 * decisions, repositories move data.</strong> When you are unsure where a piece of code
 * belongs, ask which of those three it is doing.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // class-level default; write methods override it below
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /*
     * Constructor injection via Lombok's @RequiredArgsConstructor, which generates a
     * constructor taking every final field. Spring sees one constructor and autowires it -
     * no @Autowired annotation needed since Spring 4.3.
     *
     * Prefer this to field injection (@Autowired on the field), for reasons that are
     * practical rather than stylistic:
     *   - final means genuinely immutable, and thread-safe by construction
     *   - the object cannot exist in a half-built state with null dependencies
     *   - a test can call `new ProductService(mockRepo, mapper)` with no Spring at all
     *   - a constructor with eight parameters becomes visibly painful, which is useful
     *     feedback that the class is doing too much. Field injection hides that.
     */
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    /**
     * All products, newest first.
     *
     * <p>Returning an unbounded list is fine with five seed rows and a real problem with
     * fifty thousand - it loads every row into memory and serializes them all. Production
     * code takes a {@code Pageable} and returns a {@code Page}. Left simple here so the
     * layering stays in focus, but this is a genuine limitation, not an oversight.
     */
    public List<ProductResponse> findAll() {
        return productRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    public ProductResponse findById(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toResponse)
                // orElseThrow with a supplier: the exception is only constructed if the
                // product is genuinely missing. Passing the exception directly would build
                // it (and fill in its stack trace) on every successful call too.
                .orElseThrow(() -> ProductNotFoundException.byId(id));
    }

    /**
     * Look up by business key. This is the method other services will effectively be calling
     * from Phase 3 - inventory-service knows SKUs, not our internal ids.
     */
    public ProductResponse findBySku(String sku) {
        return productRepository.findBySku(sku)
                .map(productMapper::toResponse)
                .orElseThrow(() -> ProductNotFoundException.bySku(sku));
    }

    /**
     * Creates a product.
     *
     * <p>{@code @Transactional} (read-write, overriding the class default) opens a transaction
     * on entry and commits on return, rolling back if a {@link RuntimeException} escapes.
     *
     * <p><strong>Why the transaction boundary belongs here and not in the controller or
     * repository.</strong> A repository method is a single statement - too small to be a
     * unit of business work. A controller is too big and knows about HTTP. The service method
     * is exactly one thing the business wants to happen, atomically. It becomes obvious the
     * moment a method does two writes: "insert product, then insert an audit row" must either
     * both happen or neither, and only a boundary that wraps both can promise that. Put it in
     * the repository and you get two independent transactions and, eventually, an audit log
     * that disagrees with the data.
     *
     * <p>One caveat worth knowing early: Spring implements this with a proxy around the bean,
     * so {@code @Transactional} only applies to calls that arrive <em>through</em> the proxy.
     * If a method in this class calls another method in this class directly, the annotation on
     * the inner one is ignored entirely - a silent failure that has confused every Spring
     * developer at least once.
     */
    @Transactional
    public ProductResponse create(ProductRequest request) {
        // A friendly, specific 409 in the common case. It cannot be a guarantee - two
        // concurrent requests can both pass this line - which is why the UNIQUE constraint
        // exists in V1__create_products_table.sql and GlobalExceptionHandler catches the
        // resulting DataIntegrityViolationException. Check for the message, constrain for
        // the correctness.
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }

        Product saved = productRepository.save(productMapper.toEntity(request));
        log.info("Created product id={} sku={}", saved.getId(), saved.getSku());

        return productMapper.toResponse(saved);
    }

    /**
     * Updates the mutable fields of an existing product.
     *
     * <p>There is no {@code save()} call, and that is not a bug. {@code findById} inside a
     * transaction returns a <em>managed</em> entity: Hibernate keeps it in the persistence
     * context, compares it against its original state at commit time (dirty checking), and
     * issues an UPDATE for whatever changed. Mutating a managed entity <em>is</em> the write.
     *
     * <p>Calling {@code save()} anyway would be harmless but redundant. What is <em>not</em>
     * harmless is expecting the same behaviour outside a transaction - there the entity is
     * detached, nothing tracks it, and your change is silently discarded. That asymmetry is
     * the most common source of "my update didn't persist".
     */
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ProductNotFoundException.byId(id));

        productMapper.updateEntity(product, request);
        log.info("Updated product id={} sku={}", product.getId(), product.getSku());

        return productMapper.toResponse(product);
    }

    /**
     * Deletes a product.
     *
     * <p>The existence check makes this endpoint honest: {@code deleteById} on a missing row
     * is a no-op, so without it every DELETE would return 204 and a client could never tell
     * whether it deleted something or nothing.
     *
     * <p>A real catalogue would soft-delete instead (a {@code deleted_at} column), because
     * order-service will hold references to these SKUs from Phase 3 and a hard delete leaves
     * historical orders pointing at a product that no longer exists. In a monolith a foreign
     * key would stop you; across service boundaries there are no foreign keys, so nothing
     * stops you but judgement. Worth sitting with - it is the first real consequence of
     * splitting the database.
     */
    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw ProductNotFoundException.byId(id);
        }

        productRepository.deleteById(id);
        log.info("Deleted product id={}", id);
    }
}
