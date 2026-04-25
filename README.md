## Elasticsearch Demo — Query Decomposer + Product Search

This project is a small Spring Boot service that:

- **Creates a sample `products` index** in Elasticsearch (if it doesn’t exist) and seeds a few products
- **Searches products** using a *restricted, safer subset* of Elasticsearch query DSL
- **Decomposes queries** into readable “components”
- Optionally returns **impact counts** (how many docs each component matches), similar to a lightweight “explain”

---

## Requirements

- **Java 21**
- **Elasticsearch running locally** (default: `http://localhost:9200`)

If Elasticsearch is on a different URL, set:

- `elasticsearch.url` (example: `http://localhost:9200`)

---

## How to run

From the project folder:

```bash
./mvnw spring-boot:run
```

The app runs on `http://localhost:8080`.

On startup, the app will:

- Create index `products` (if missing)
- Add mappings: `name`, `category`, `price`, `inStock`
- Seed demo documents (default: 3)

### Seeding a larger dataset (50,000 docs)

The seeder only runs **when the `products` index does not already exist**. To generate a large dataset:

1) Delete the index (optional, only if it already exists):

```bash
curl --location --request DELETE 'http://localhost:9200/products'
```

2) Start the app with a higher seed count:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--demo.seed.count=50000"
```

This will bulk-index ~50,000 products with fields:

- `name` (text)
- `category` (keyword)
- `brand` (keyword)
- `price` (double)
- `inStock` (boolean)
- `rating` (double)

---

## Endpoints

### OpenAPI / Swagger UI

When the app is running:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

---

### Health check

- **GET** `/ping-es`

Returns basic cluster info if Elasticsearch is reachable.

---

### Product search (subset of ES DSL)

- **POST** `/api/search-products`
- Accepts `bool` payload with `must` / `filter` / `should`
- Supported operations: `match`, `term`, `range`, and nested `bool`

Example:

```bash
curl --location 'http://localhost:8080/api/search-products' \
--header 'Accept: application/json' \
--header 'Content-Type: application/json' \
--data '{"bool":{"must":[{"match":{"category":"electronics"}}],"filter":[{"range":{"price":{"lte":30000}}}]}}'
```

---

### Impact table (progressive narrowing)

This is the “assessment table” output: **base count**, then **cumulative counts** after applying each clause in order, plus a **final result** row.

Each row is returned as JSON (`component`, `resultsAfterApplying`, `explanation`) so it can be rendered as a table in a UI (or Postman Visualizer).

#### Option A — incremental `_count` (simple, multiple round-trips)

- **POST** `/api/decompose-impact-table`
- Implementation: runs Elasticsearch `_count` once per stage.
- Best when: you want maximum clarity and correctness with minimal aggregation complexity.

Example:

```bash
curl --location "http://localhost:8080/api/decompose-impact-table" ^
--header "Accept: application/json" ^
--header "Content-Type: application/json" ^
--data "{\"bool\":{\"must\":[{\"match\":{\"category\":\"electronics\"}}],\"filter\":[{\"range\":{\"price\":{\"gte\":50,\"lte\":200}}},{\"term\":{\"inStock\":true}},{\"range\":{\"rating\":{\"gte\":4}}}],\"should\":[{\"term\":{\"brand\":\"Sony\"}},{\"term\":{\"brand\":\"Samsung\"}}]}}"
```

#### Option B — `filters` aggregation (one Elasticsearch request)

- **POST** `/api/decompose-impact-table-aggregations`
- Implementation: a single `_search` with `size=0` and a **`filters` aggregation** containing one keyed bucket per cumulative stage.
- Best when: you want to **minimize round-trips** to Elasticsearch (important for many stages / high QPS).

Example:

```bash
curl --location "http://localhost:8080/api/decompose-impact-table-aggregations" ^
--header "Accept: application/json" ^
--header "Content-Type: application/json" ^
--data "{\"bool\":{\"must\":[{\"match\":{\"category\":\"electronics\"}}],\"filter\":[{\"range\":{\"price\":{\"gte\":50,\"lte\":200}}},{\"term\":{\"inStock\":true}},{\"range\":{\"rating\":{\"gte\":4}}}],\"should\":[{\"term\":{\"brand\":\"Sony\"}},{\"term\":{\"brand\":\"Samsung\"}}]}}"
```

**Trade-offs (A vs B)**

- **Correctness / simplicity**: Option A is easiest to reason about.
- **Performance**: Option B reduces network + coordination overhead by batching counts.
- **Limits**: Option B builds one aggregation with **N buckets** (one per stage). Very large N can become heavy; Option A can be easier to tune (timeouts, parallelism) but uses more requests.

---

## Logging & execution timing

Services log:

- **Incoming payload** (as received)
- **Execution time** in milliseconds

---

## Error handling

The API returns consistent JSON errors:

- **400** for invalid JSON or invalid query format
- **503** for Elasticsearch connection / IO failures

Example error shape:

```json
{
  "timestamp": "2026-04-24T12:34:56Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid query format: expected 'bool', 'match', 'term', or 'range'.",
  "path": "/api/decompose-impact-table"
}
```

---

## Design decisions

- **Restricted DSL on purpose**: only `match`, `term`, `range`, and `bool` nesting are supported to keep parsing predictable and safe.
- **Controller → Service**: controllers are thin; parsing/search logic lives in services.
- **Progressive impact table**:
  - `/api/decompose-impact-table` uses repeated `_count` (Option A)
  - `/api/decompose-impact-table-aggregations` uses a single `filters` aggregation (Option B)
- **Seeded index**: `ProductIndexInitializer` creates a demo dataset automatically so the API is usable immediately.

