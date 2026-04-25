package com.example.elasticsearchdemo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.example.elasticsearchdemo.model.Product;
import com.example.elasticsearchdemo.queryops.LeafQueryOperatorHandler;
import com.example.elasticsearchdemo.queryops.LeafQueryOperatorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ProductSearchService {

    private static final String INDEX_NAME = "products";
    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ElasticsearchClient elasticsearchClient;
    private final LeafQueryOperatorRegistry operatorRegistry;

    public ProductSearchService(ElasticsearchClient elasticsearchClient, LeafQueryOperatorRegistry operatorRegistry) {
        this.elasticsearchClient = elasticsearchClient;
        this.operatorRegistry = operatorRegistry;
    }

    public List<Product> search(Map<String, Object> payload) throws IOException {
        long startNs = System.nanoTime();
        try {
            log.info("Search request received: {}", payload);

            validatePayload(payload);
            Query query = buildQuery(payload);
            SearchResponse<Product> response = elasticsearchClient.search(
                    s -> s.index(INDEX_NAME).query(query),
                    Product.class
            );

            List<Product> products = new ArrayList<>();
            response.hits().hits().forEach(hit -> {
                Product source = hit.source();
                if (source != null) {
                    products.add(source);
                }
            });
            return products;
        } finally {
            log.info("Search completed in {} ms", (System.nanoTime() - startNs) / 1_000_000);
        }
    }

    void validatePayload(Map<String, Object> payload) {
        Object root = extractQueryRoot(payload);
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new IllegalArgumentException("Invalid payload: expected object.");
        }

        Object boolObj = rootMap.get("bool");
        if (!(boolObj instanceof Map<?, ?> boolMap)) {
            throw new IllegalArgumentException("Invalid payload: expected 'bool' object.");
        }

        validateBool(boolMap);
    }

    private Query buildQuery(Map<String, Object> payload) {
        Object root = extractQueryRoot(payload);
        if (!(root instanceof Map<?, ?> rootMap)) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        Object boolObj = rootMap.get("bool");
        if (!(boolObj instanceof Map<?, ?> boolMap)) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        return buildBoolQuery(boolMap);
    }

    private Object extractQueryRoot(Map<String, Object> payload) {
        if (payload.containsKey("query") && payload.get("query") instanceof Map<?, ?> queryMap) {
            return queryMap;
        }
        return payload;
    }

    private void addClauseQueries(BoolQuery.Builder boolBuilder, Map<?, ?> boolMap, String clause) {
        List<?> clauses = normalizeClauses(boolMap.get(clause));
        for (Object item : clauses) {
            if (!(item instanceof Map<?, ?> clauseMap)) {
                continue;
            }
            List<Query> parsed = parseClause(clauseMap);
            for (Query query : parsed) {
                switch (clause) {
                    case "must" -> boolBuilder.must(query);
                    case "filter" -> boolBuilder.filter(query);
                    case "should" -> boolBuilder.should(query);
                    default -> {
                    }
                }
            }
        }
    }

    private List<?> normalizeClauses(Object clauseObj) {
        if (clauseObj instanceof List<?> list) {
            return list;
        }
        if (clauseObj instanceof Map<?, ?> map) {
            return List.of(map);
        }
        return List.of();
    }

    private void validateClause(Map<?, ?> boolMap, String clauseName) {
        Object clauseObj = boolMap.get(clauseName);
        if (clauseObj == null) {
            return;
        }

        List<?> clauses = normalizeClauses(clauseObj);
        if (clauses.isEmpty()) {
            throw new IllegalArgumentException("Invalid '" + clauseName + "' clause: expected object or list.");
        }

        for (Object clauseItem : clauses) {
            if (!(clauseItem instanceof Map<?, ?> clauseMap)) {
                throw new IllegalArgumentException("Invalid '" + clauseName + "' item: expected object.");
            }

            boolean hasSupportedOperation = false;
            for (Object key : clauseMap.keySet()) {
                String operation = String.valueOf(key);
                if (operation.equals("bool")) {
                    Object nested = clauseMap.get(key);
                    if (!(nested instanceof Map<?, ?> nestedBoolMap)) {
                        throw new IllegalArgumentException("Invalid 'bool' operation: expected object.");
                    }
                    validateBool(nestedBoolMap);
                    hasSupportedOperation = true;
                    continue;
                }

                LeafQueryOperatorHandler handler = operatorRegistry.getOrNull(operation);
                if (handler == null) {
                    throw new IllegalArgumentException("Unsupported operation: '" + operation + "'. Supported: match, term, range, bool.");
                }
                handler.validate(clauseMap.get(key));
                hasSupportedOperation = true;
            }

            if (!hasSupportedOperation) {
                throw new IllegalArgumentException("Invalid '" + clauseName + "' item: empty operation object.");
            }
        }
    }

    private void validateBool(Map<?, ?> boolMap) {
        validateClause(boolMap, "must");
        validateClause(boolMap, "filter");
        validateClause(boolMap, "should");
    }

    private void validateRangeOperation(Object rangeObj) {
        if (!(rangeObj instanceof Map<?, ?> rangeMap)) {
            throw new IllegalArgumentException("Invalid 'range' operation: expected object.");
        }

        for (Object fieldConfigObj : rangeMap.values()) {
            if (!(fieldConfigObj instanceof Map<?, ?> fieldConfig)) {
                throw new IllegalArgumentException("Invalid 'range' field config: expected object.");
            }
            boolean hasBounds = fieldConfig.containsKey("gte")
                    || fieldConfig.containsKey("lte")
                    || fieldConfig.containsKey("gt")
                    || fieldConfig.containsKey("lt");
            if (!hasBounds) {
                throw new IllegalArgumentException("Invalid 'range' operation: provide at least one of gte, lte, gt, lt.");
            }
        }
    }

    private List<Query> parseClause(Map<?, ?> clauseMap) {
        List<Query> queries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : clauseMap.entrySet()) {
            String operation = String.valueOf(entry.getKey());
            Object operationObj = entry.getValue();

            if ("bool".equals(operation)) {
                addBoolQueries(operationObj, queries);
                continue;
            }

            LeafQueryOperatorHandler handler = operatorRegistry.getOrNull(operation);
            if (handler == null) {
                continue;
            }
            queries.addAll(handler.toQueries(operationObj));
        }
        return queries;
    }

    private void addBoolQueries(Object operationObj, List<Query> queries) {
        if (!(operationObj instanceof Map<?, ?> boolMap)) {
            return;
        }
        queries.add(buildBoolQuery(boolMap));
    }

    private Query buildBoolQuery(Map<?, ?> boolMap) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        addClauseQueries(boolBuilder, boolMap, "must");
        addClauseQueries(boolBuilder, boolMap, "filter");
        addClauseQueries(boolBuilder, boolMap, "should");
        return Query.of(q -> q.bool(boolBuilder.build()));
    }

    private FieldValue toFieldValue(Object value) {
        if (value instanceof Boolean b) {
            return FieldValue.of(b);
        }
        if (value instanceof Long l) {
            return FieldValue.of(l);
        }
        if (value instanceof Integer i) {
            return FieldValue.of(i.longValue());
        }
        if (value instanceof Double d) {
            return FieldValue.of(d);
        }
        if (value instanceof Float f) {
            return FieldValue.of((double) f);
        }
        return FieldValue.of(String.valueOf(value));
    }
}
