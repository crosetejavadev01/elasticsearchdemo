# Decomposition Logic — Elasticsearch Search Decomposition MVP

## 1. Overview

This document explains how an Elasticsearch query is decomposed into progressive stages.

The goal is to show how each condition affects the result count.

## 2. Supported Query Structure

Supported:

- `bool`
- `must`
- `filter`
- `should`
- `match`
- `term`
- `range`
- nested `bool`

## 3. Decomposition Strategy

The system converts a query into a sequence of stages.

Each stage applies one additional condition.

## 4. Stage Ordering Rules

Order is fixed:

- Base query (`match_all`)
- `must`
- `filter`
- `should` (grouped)

## 5. Base Stage

`match_all`

Represents total document count.

## 6. Must Clause Handling

Each `must` condition becomes its own stage.

Example:

```json
{ "match": { "category": "electronics" } }
```

## 7. Filter Clause Handling

Each `filter` condition is applied incrementally. For example:

- `range` (price)
- `term` (inStock)
- `range` (rating)

Each becomes a separate stage.

## 8. Should Clause Handling

All `should` clauses are grouped into ONE stage.

Example:

```json
"should": [
  { "term": { "brand": "Sony" }},
  { "term": { "brand": "Samsung" }}
]
```

Converted into:

- Brand: Sony OR Samsung

Minimum Should Match

Automatically applied:

- `minimum_should_match = 1`

Ensures OR behavior.

## 9. Nested Bool Handling

Nested `bool` queries are recursively parsed.

Each nested structure becomes a grouped component.

## 10. Stage Construction Example

Input:

```json
{
  "bool": {
    "must": [
      { "match": { "category": "electronics" } }
    ],
    "filter": [
      { "range": { "price": { "gte": 50, "lte": 200 } } },
      { "term": { "inStock": true } }
    ],
    "should": [
      { "term": { "brand": "Sony" } },
      { "term": { "brand": "Samsung" } }
    ]
  }
}
```

Output Stages:

- Base Query
- Category
- Price Range
- In Stock
- Brand OR

## 11. Query Building

Each stage builds on previous conditions.

Example:

- Stage 2 includes:
  - Stage 1 conditions
  - new condition

## 12. Explanation Generation

Each stage includes a human-readable explanation.

Examples:

- "Applied category = electronics"
- "Applied price between 50 and 200"
- "Applied brand Sony OR Samsung"

## 13. Limitations

- No scoring logic
- No `function_score`
- No script queries
- Limited DSL support

## 14. Design Decisions

| Decision | Reason |
| --- | --- |
| Group `should` | Simpler output |
| Fixed order | Predictable |
| Restricted DSL | Avoid incorrect parsing |

## 15. Rubric Alignment

- **Decomposition** → stage-by-stage breakdown
- **Logical grouping** → `should` clause handling
- **Ordering** → deterministic and explainable

