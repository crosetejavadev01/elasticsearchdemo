package com.example.elasticsearchdemo.service;

import com.example.elasticsearchdemo.model.QueryComponent;
import com.example.elasticsearchdemo.queryops.LeafQueryOperatorHandler;
import com.example.elasticsearchdemo.queryops.LeafQueryOperatorRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses incoming query payloads into decomposed {@link QueryComponent}s.
 * <p>
 * This is intentionally separate from impact counting so parsing can evolve independently
 * (and be tested without Elasticsearch).
 */
@Component
public class QueryComponentParser {

    private final LeafQueryOperatorRegistry operatorRegistry;

    public QueryComponentParser(LeafQueryOperatorRegistry operatorRegistry) {
        this.operatorRegistry = operatorRegistry;
    }

    public List<QueryComponent> parse(Map<String, Object> payload) {
        Object root = extractQueryRoot(payload);
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new IllegalArgumentException("Invalid query format: expected JSON object payload.");
        }

        // Common shape: { "bool": { ... } } or { "query": { "bool": { ... } } }
        if (rootMap.containsKey("bool") && rootMap.get("bool") instanceof Map<?, ?> boolMap) {
            List<QueryComponent> components = new ArrayList<>();
            addBoolChildren(boolMap, "must", components);
            addBoolChildren(boolMap, "filter", components);
            addBoolChildren(boolMap, "should", components);
            return components;
        }

        // Otherwise, attempt to parse a single query object (leaf operator or nested bool).
        List<QueryComponent> components = parseQueryObject("root", rootMap);
        if (components.isEmpty()) {
            throw new IllegalArgumentException("Invalid query format: expected 'bool' or a supported operator object.");
        }
        return components;
    }

    private Object extractQueryRoot(Map<String, Object> payload) {
        if (payload.containsKey("query") && payload.get("query") instanceof Map<?, ?> queryMap) {
            return queryMap;
        }
        return payload;
    }

    private void addBoolChildren(Map<?, ?> boolMap, String clauseType, List<QueryComponent> out) {
        List<?> clauses = normalizeToList(boolMap.get(clauseType));
        for (Object clause : clauses) {
            if (!(clause instanceof Map<?, ?> clauseMap)) {
                continue;
            }
            out.addAll(parseQueryObject(clauseType, clauseMap));
        }
    }

    private List<?> normalizeToList(Object obj) {
        if (obj == null) {
            return List.of();
        }
        if (obj instanceof List<?> list) {
            return list;
        }
        if (obj instanceof Map<?, ?> map) {
            return List.of(map);
        }
        return List.of();
    }

    private List<QueryComponent> parseQueryObject(String type, Map<?, ?> queryObj) {
        List<QueryComponent> out = new ArrayList<>();

        // Nested bool support
        Object boolObj = queryObj.get("bool");
        if (boolObj instanceof Map<?, ?> boolMap) {
            out.add(buildBoolNode(type, boolMap));
            return out;
        }

        // Leaf operators are handled via the registry.
        for (Map.Entry<?, ?> entry : queryObj.entrySet()) {
            String op = String.valueOf(entry.getKey());
            if ("bool".equals(op)) {
                continue;
            }
            LeafQueryOperatorHandler handler = operatorRegistry.getOrNull(op);
            if (handler == null) {
                continue;
            }
            out.addAll(handler.toComponents(type, entry.getValue()));
        }

        return out;
    }

    private QueryComponent buildBoolNode(String type, Map<?, ?> boolMap) {
        QueryComponent node = new QueryComponent();
        node.setType(type);
        node.setOperation("bool");
        node.setExplanation(explainBool(boolMap));

        List<QueryComponent> children = new ArrayList<>();
        addBoolChildren(boolMap, "must", children);
        addBoolChildren(boolMap, "filter", children);
        addBoolChildren(boolMap, "should", children);
        node.setChildren(children.isEmpty() ? null : children);
        return node;
    }

    private String explainBool(Map<?, ?> boolMap) {
        int mustCount = normalizeToList(boolMap.get("must")).size();
        int filterCount = normalizeToList(boolMap.get("filter")).size();
        int shouldCount = normalizeToList(boolMap.get("should")).size();
        return "Boolean query with " + mustCount + " must, " + filterCount + " filter, " + shouldCount + " should clause(s)";
    }
}

