package com.inventory.product_service.controller;

import com.inventory.product_service.dto.ProductRequest;
import com.inventory.product_service.dto.ProductResponse;
import com.inventory.product_service.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * HTTP endpoints for the product catalogue.
 *
 * <p>Every method here does the same three things and nothing else: accept a request, call
 * one service method, shape the response. There is no business logic, no repository access
 * and no try/catch - exceptions propagate to
 * {@link com.inventory.product_service.exception.GlobalExceptionHandler}. A controller that
 * stays this thin is a sign the layering is holding; when you find yourself wanting an
 * {@code if} here, the decision almost certainly belongs in the service.
 *
 * <p>From Phase 5 nothing calls these paths directly - the API gateway does, on the client's
 * behalf. The URLs stay exactly the same, which is rather the point of a gateway.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** GET /api/products */
    @GetMapping
    public List<ProductResponse> findAll() {
        // Returning the list directly rather than wrapping it in ResponseEntity. Spring
        // serializes it and uses 200 by default, so the wrapper would add nothing. Reach for
        // ResponseEntity when you need to control the status or add headers - as create()
        // below does.
        return productService.findAll();
    }

    /** GET /api/products/{id} - our internal id. */
    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        // @PathVariable binds the URL segment and converts it to Long. A non-numeric value
        // never reaches this method: Spring throws MethodArgumentTypeMismatchException during
        // binding, which the handler turns into a 400.
        return productService.findById(id);
    }

    /**
     * GET /api/products/sku/{sku} - lookup by business key.
     *
     * <p>Two paths to one resource is a deliberate choice, not a mistake. Other services
     * know SKUs; only this service knows ids. An alternative design would be
     * {@code GET /api/products?sku=...} as a filter on the collection. Both are defensible;
     * what matters is picking one convention and holding to it across all six services,
     * because inconsistency is what makes an API exhausting to use.
     */
    @GetMapping("/sku/{sku}")
    public ProductResponse findBySku(@PathVariable String sku) {
        return productService.findBySku(sku);
    }

    /**
     * POST /api/products
     *
     * <p>{@code @Valid} is what actually triggers Bean Validation on the request body. Without
     * it, every constraint in {@link ProductRequest} is silently ignored and invalid data
     * sails through to the database - the single most common validation bug in Spring.
     *
     * <p>Returns <strong>201 Created</strong> with a {@code Location} header pointing at the
     * new resource, which is what the HTTP spec asks for and what lets a client follow up
     * without string-building URLs itself. Plenty of APIs return 200 here; 201 tells the
     * client something more precise, at no cost.
     */
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);

        return ResponseEntity
                .created(URI.create("/api/products/" + created.id()))
                .body(created);
    }

    /**
     * PUT /api/products/{id}
     *
     * <p>PUT replaces the mutable fields wholesale, so a field omitted from the body is set
     * to null rather than left alone. That is PUT's defined semantics - "make the resource
     * look like this" - and it is why partial updates properly belong to PATCH. Clients that
     * expect PUT to merge are a recurring source of quietly nulled columns.
     */
    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {

        return productService.update(id, request);
    }

    /**
     * DELETE /api/products/{id}
     *
     * <p>204 No Content: it succeeded, and there is deliberately nothing to send back.
     * {@code ResponseEntity<Void>} is how you express "no body" in the type system.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/products/ping - kept from Phase 0 as a trivial liveness check that touches no
     * database. Useful for confirming routing works in isolation once the gateway arrives in
     * Phase 5: if {@code /ping} answers but the other endpoints fail, the problem is the
     * database, not the routing.
     */
    @GetMapping("/ping")
    public String ping() {
        return "product-service is alive";
    }
}
