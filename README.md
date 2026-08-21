# product-service

The product catalogue for the [Inventory Management System](../platform). Owns the
`products` table and is the only service permitted to touch it — everything else asks over
HTTP.

Part of a polyrepo: this service is versioned, built, tested and deployed independently of
every other service in the system. Architecture docs and the Compose topology live in the
**platform** repository.

| | |
| --- | --- |
| Port | `8081` |
| Database | `product_db` (PostgreSQL) — private to this service |
| Java / Spring Boot | 21 / 3.5.15 |
| Public identifier | `sku` (other services never use our internal `id`) |

## Run it

The database comes from the platform repo's Compose file:

```bash
cd ../platform && docker compose up -d      # wait for STATUS "healthy"
cd ../product-service && ./mvnw spring-boot:run
```

```bash
curl -s localhost:8081/api/products | jq                  # 5 seeded products
curl -s localhost:8081/api/products/sku/IPHONE-15 | jq
curl -s localhost:8081/actuator/health | jq               # includes a real "db" check
```

`requests.http` has every endpoint plus all the failure cases — click-to-run in IntelliJ or
VS Code.

```bash
./mvnw test        # 10 web-layer tests, no database required
```

## API

| Method | Path | Returns |
| --- | --- | --- |
| `GET` | `/api/products` | `200` all products, newest first |
| `GET` | `/api/products/{id}` | `200`, or `404` if absent |
| `GET` | `/api/products/sku/{sku}` | `200`, or `404` — the lookup other services use |
| `POST` | `/api/products` | `201` + `Location`, `400` invalid, `409` duplicate SKU |
| `PUT` | `/api/products/{id}` | `200`, `400`, `404` |
| `DELETE` | `/api/products/{id}` | `204`, or `404` |
| `GET` | `/api/products/ping` | `200` liveness, touches no database |
| `GET` | `/actuator/health` | `200` including a live `db` component |

Every error returns the same shape, so a client writes one error path rather than one per
endpoint:

```json
{
  "timestamp": "2026-08-21T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/products",
  "fieldErrors": { "sku": "sku is required" }
}
```

## Structure

```
src/main/java/com/inventory/product_service/
├── controller/   HTTP only — parse, delegate, respond. No logic
├── service/      business logic + the @Transactional boundary. Knows nothing of HTTP
├── repository/   Spring Data JPA interface; the implementation is generated
├── entity/       JPA mapping. Never leaves this service
├── dto/          the public API contract (request / response records)
├── mapper/       entity ↔ DTO, hand-written so the boundary stays visible
└── exception/    domain exceptions + @RestControllerAdvice → one error shape

src/main/resources/db/migration/    Flyway owns the schema; Hibernate only validates it
```

The layering rule in one line: **controllers translate protocols, services make decisions,
repositories move data.**

## Schema

Flyway migrations in `src/main/resources/db/migration` are the single source of truth.
`spring.jpa.hibernate.ddl-auto=validate` means Hibernate checks its mappings against the live
schema at startup and refuses to boot on a mismatch — it never alters anything.

Migrations are **append-only**. Once a version has been applied anywhere, editing it fails a
checksum check; new changes go in a new `V{n}__*.sql`.

## Notes for the reader

- Money is `BigDecimal` against `NUMERIC(12,2)`, never `double` — binary floating point
  cannot represent `0.1` exactly.
- `ProductService.update()` never calls `save()`. Inside a transaction the entity is
  *managed*, so Hibernate's dirty checking issues the `UPDATE` at commit.
- `create()` checks `existsBySku` **and** the table has a `UNIQUE` constraint. The check
  gives a clear 409 in the common case; the constraint is what makes it actually true under
  concurrency. Check for the message, constrain for the correctness.
- Known gaps, deliberate: `findAll()` is unbounded (production takes a `Pageable`),
  credentials sit in `application.yaml` (they move to the config server in Phase 6), and
  there is no full-context integration test yet — that needs Testcontainers rather than H2,
  whose SQL dialect differs from the Postgres we actually deploy on.

## CI

`.github/workflows/ci.yml` runs `./mvnw verify` on every push and PR to `main`, with Maven
caching, and uploads test reports and the jar. Docker image publishing arrives in Phase 2.
