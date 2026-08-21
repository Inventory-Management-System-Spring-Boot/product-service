package com.inventory.product_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.product_service.dto.ProductRequest;
import com.inventory.product_service.dto.ProductResponse;
import com.inventory.product_service.exception.DuplicateSkuException;
import com.inventory.product_service.exception.ProductNotFoundException;
import com.inventory.product_service.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the HTTP layer in isolation.
 *
 * <p>{@code @WebMvcTest} starts a <em>slice</em> of the application: Spring MVC, Jackson,
 * Bean Validation and any {@code @RestControllerAdvice} - but no JPA, no connection pool, no
 * embedded server. So these tests need no database and run in well under a second.
 *
 * <p>{@link MockitoBean} replaces the real {@link ProductService} with a Mockito mock in the
 * test's application context, which is what makes the isolation possible. (It supersedes
 * {@code @MockBean}, deprecated since Spring Boot 3.4.)
 *
 * <p>What is worth testing at this layer is the translation, not the business rules: does a
 * bad payload become a 400 with useful field errors, does a domain exception become the right
 * status code, does POST return 201 with a Location header. The service's own logic gets
 * tested by calling the service directly - no HTTP needed.
 *
 * <p>There is deliberately no full {@code @SpringBootTest} here yet. Now that JPA is on the
 * classpath, loading the whole context requires a real Postgres. The right answer is
 * Testcontainers, which spins one up per test run - and that arrives in a later phase. The
 * wrong answer, common in tutorials, is to swap in H2 for tests: your tests would then
 * validate against a database with different SQL, a different dialect and different
 * constraint behaviour from the one you actually deploy on, which is how a green build ships
 * a broken migration.
 */
@WebMvcTest(ProductController.class)
@DisplayName("ProductController (web layer)")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private static final ProductResponse SAMPLE = new ProductResponse(
            1L, "IPHONE-15", "iPhone 15", "Apple smartphone, 128GB",
            new BigDecimal("79999.00"), Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    @DisplayName("GET /api/products returns 200 and the list")
    void findAll_returnsList() throws Exception {
        given(productService.findAll()).willReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].sku").value("IPHONE-15"))
                .andExpect(jsonPath("$[0].price").value(79999.00));
    }

    @Test
    @DisplayName("GET /api/products/{id} returns 200 for an existing product")
    void findById_returnsProduct() throws Exception {
        given(productService.findById(1L)).willReturn(SAMPLE);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("iPhone 15"));
    }

    @Test
    @DisplayName("GET /api/products/{id} maps ProductNotFoundException to 404")
    void findById_missing_returns404() throws Exception {
        given(productService.findById(999L)).willThrow(ProductNotFoundException.byId(999L));

        // This asserts the GlobalExceptionHandler wiring, not the service: a domain
        // exception must come out as a 404 in our standard ApiError shape.
        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/products/999"))
                .andExpect(jsonPath("$.message").value("No product found with id 999"));
    }

    @Test
    @DisplayName("GET /api/products/{id} with a non-numeric id returns 400")
    void findById_nonNumericId_returns400() throws Exception {
        // "abc" cannot bind to Long, so the request fails during argument resolution and the
        // controller method is never entered - hence the `never()` verification below.
        mockMvc.perform(get("/api/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        then(productService).should(never()).findById(any());
    }

    @Test
    @DisplayName("POST /api/products returns 201 with a Location header")
    void create_returns201() throws Exception {
        ProductRequest request = new ProductRequest(
                "PIXEL-9", "Google Pixel 9", "Android smartphone", new BigDecimal("69999.00"));
        given(productService.create(any(ProductRequest.class))).willReturn(SAMPLE);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/1"))
                .andExpect(jsonPath("$.sku").value("IPHONE-15"));
    }

    @Test
    @DisplayName("POST /api/products rejects an invalid body with 400 and per-field errors")
    void create_invalidBody_returns400WithFieldErrors() throws Exception {
        // Every field is wrong in a different way: blank name, lowercase sku (fails the
        // @Pattern), negative price. One request, three constraint violations.
        String body = """
                {
                  "sku": "lowercase-sku",
                  "name": "  ",
                  "price": -5.00
                }
                """;

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.sku").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists());

        // The important assertion: validation ran BEFORE the service was touched. Invalid
        // data never reached the business layer at all.
        then(productService).should(never()).create(any());
    }

    @Test
    @DisplayName("POST /api/products maps a duplicate SKU to 409")
    void create_duplicateSku_returns409() throws Exception {
        ProductRequest request = new ProductRequest(
                "IPHONE-15", "iPhone 15", null, new BigDecimal("79999.00"));
        willThrow(new DuplicateSkuException("IPHONE-15"))
                .given(productService).create(any(ProductRequest.class));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("A product with sku 'IPHONE-15' already exists"));
    }

    @Test
    @DisplayName("POST /api/products rejects malformed JSON with 400")
    void create_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Malformed request body - expected valid JSON"));
    }

    @Test
    @DisplayName("DELETE /api/products/{id} returns 204 with no body")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());

        then(productService).should().delete(eq(1L));
    }

    @Test
    @DisplayName("GET /api/products/ping still works and never touches the service")
    void ping_returnsAlive() throws Exception {
        // Confirms that the literal /ping path wins over the /{id} pattern. Spring prefers
        // the more specific match, so "ping" is never fed to the Long converter.
        mockMvc.perform(get("/api/products/ping"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string("product-service is alive"));
    }
}
