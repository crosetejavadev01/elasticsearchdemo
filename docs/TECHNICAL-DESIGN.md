# Technical Design — Elasticsearch Search Decomposition MVP

## 1. Overview

This document describes the system architecture of the Elasticsearch Search Decomposition MVP.

The system decomposes a structured subset of Elasticsearch Query DSL into progressive stages and computes the result count at each stage using two approaches:

- **Option A**: Multiple `_count` requests
- **Option B**: Single `_search` request with aggregations

## 2. High-Level Architecture

Client (Swagger / Postman)  
↓  
Controller Layer  
↓  
Service Layer  
↓  
QueryComponentParser  
↓  
Operator Registry (`queryops`)  
↓  
Elasticsearch Client

## 3. Layer Responsibilities

### 3.1 Controller Layer

Handles incoming HTTP requests.

**Endpoints**

- `POST /api/decompose-impact-table`
- `POST /api/decompose-impact-table-aggregations`

**Responsibilities**

- Accept JSON request body (payload is passed through as a generic map)
- Forward payload to service layer
- Return structured JSON response
- Rely on centralized HTTP error mapping (via global exception handler)

**Validation note (current implementation)**

The controller does not do explicit bean validation; payload shape/operator validation occurs during parsing/building in lower layers and is surfaced as `400` via the global exception handler.

**Future improvement (recommended)**

- Introduce request DTOs for the public endpoints (e.g., a `DecomposeRequest` with a `Map<String, Object> query` field or a typed representation of supported DSL).
- Add Bean Validation annotations (`@Valid`, `@NotNull`, `@NotEmpty`, custom constraints where needed) so malformed requests fail fast at the controller boundary.
- Keep error responses consistent by mapping `MethodArgumentNotValidException` (and related validation exceptions) to `400` in `ApiExceptionHandler`, with field-level details if desired.

### 3.2 Service Layer

Core business logic of the application.

**Responsibilities**

**Option A (Multiple Queries)**

- Build progressive query stages
- Execute one `_count` request per cumulative stage
- Collect counts and explanations
- Return ordered results

**Option B (Aggregations)**

- Build the same progressive query stages
- Construct a single `_search` request
- Use a `filters` aggregation (one bucket per stage)
- Extract `doc_count` per stage bucket

**Additional responsibilities**

- Execution timing
- Logging
- Error propagation to centralized handler

### 3.3 QueryComponentParser

Responsible for converting raw JSON DSL into structured components.

**Responsibilities**

- Traverse query tree
- Identify `bool` → `must`, `filter`, `should`
- Extract leaf queries (via operator registry)
- Preserve logical grouping (including nested `bool`)
- Support nested `bool` queries

**Output**

- `List<QueryComponent>`

### 3.4 Operator Registry (`queryops`)

Implements extensible handling of leaf queries.

Each operator has a handler:

- `MatchOperatorHandler`
- `TermOperatorHandler`
- `RangeOperatorHandler`

**Responsibilities**

- Interpret a leaf query payload for its operator
- Generate an Elasticsearch query fragment
- Provide a human-readable explanation string

### 3.5 Elasticsearch Client

Handles communication with Elasticsearch.

**Operations**

- `_count` (Option A)
- `_search` with aggregations (Option B)

## 4. Data Flow

### Option A

Input Query  
↓  
Parse → Components  
↓  
Stage Builder  
↓  
Loop:

- Apply next condition
- Call `_count`
- Store result

### Option B

Input Query  
↓  
Parse → Components  
↓  
Stage Builder  
↓  
Build Aggregation Query  
↓  
Single `_search` call  
↓  
Extract `doc_count` per bucket

**Important note (shared logic)**

Option A and Option B share the same parsing/decomposition and stage-building logic (same `QueryComponentParser` output and same cumulative stage queries). The only difference is the counting strategy: repeated `_count` calls (Option A) vs one `_search` with `filters` aggregation (Option B).

## 5. Query Stage Model

Each stage contains:

- component name
- Elasticsearch query
- explanation
- result count

## 6. Error Handling

| Scenario | Behavior |
| --- | --- |
| Invalid JSON | 400 |
| Unsupported operator | 400 |
| Elasticsearch unavailable | 503 |
| Internal failure | 500 |

**Mapping note (current implementation)**

- Invalid JSON (`HttpMessageNotReadableException`) → `400`
- Payload/operator/shape validation failures (`IllegalArgumentException`) → `400`
- Elasticsearch/IO failures (`ElasticsearchException`, `IOException`) → `503`
- Anything else → `500`

## 7. Logging

Logs include:

- incoming payload (at service entrypoints)
- execution time (per operation)
- errors (via global exception handler)

## 8. Extendability

To add a new operator:

- Create a new handler class
- Implement the handler interface (`LeafQueryOperatorHandler`)
- Register via Spring component scanning so it is collected by the operator registry

**No changes required** in the parser or service layer for adding new *leaf* operators (as long as the new operator is handled by a registry-registered handler).

## 9. Design Trade-Offs

| Area | Decision | Reason |
| --- | --- | --- |
| DSL Scope | Restricted | Ensures correctness and keeps parsing deterministic for an MVP |
| Option A | Multiple calls | Simplicity and directness; easy to reason about counts per stage |
| Option B | Aggregations | Efficiency (single round trip); better performance at higher stage counts |
| `should` handling | Grouped | Cleaner output: `should` is an OR-group, so it is presented as a single step |

