package com.example.elasticsearchdemo.queryops;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import com.example.elasticsearchdemo.model.QueryComponent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RangeOperatorHandler implements LeafQueryOperatorHandler {

    @Override
    public String operator() {
        return "range";
    }

    @Override
    public void validate(Object operatorPayload) {
        if (!(operatorPayload instanceof Map<?, ?> rangeMap)) {
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

    @Override
    public List<Query> toQueries(Object operatorPayload) {
        if (!(operatorPayload instanceof Map<?, ?> rangeMap)) {
            return List.of();
        }

        List<Query> queries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : rangeMap.entrySet()) {
            String field = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            queries.add(buildRangeQuery(field, value));
        }
        return queries;
    }

    @Override
    public List<QueryComponent> toComponents(String clauseType, Object operatorPayload) {
        if (!(operatorPayload instanceof Map<?, ?> rangeMap)) {
            return List.of();
        }

        List<QueryComponent> out = new ArrayList<>();
        for (Map.Entry<?, ?> entry : rangeMap.entrySet()) {
            String field = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            QueryComponent c = new QueryComponent(clauseType, "range", field, value);
            c.setExplanation(explainRange(field, value));
            out.add(c);
        }
        return out;
    }

    @Override
    public Query toQuery(QueryComponent component) {
        return buildRangeQuery(component.getField(), component.getValue());
    }

    private Query buildRangeQuery(String field, Object value) {
        if (!(value instanceof Map<?, ?> rangeValues)) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        return Query.of(q -> q.range(r -> {
            r.field(field);
            Object gte = rangeValues.get("gte");
            Object lte = rangeValues.get("lte");
            Object gt = rangeValues.get("gt");
            Object lt = rangeValues.get("lt");
            if (gte != null) {
                r.gte(JsonData.of(gte));
            }
            if (lte != null) {
                r.lte(JsonData.of(lte));
            }
            if (gt != null) {
                r.gt(JsonData.of(gt));
            }
            if (lt != null) {
                r.lt(JsonData.of(lt));
            }
            return r;
        }));
    }

    private String explainRange(String field, Object value) {
        if (!(value instanceof Map<?, ?> rangeValues)) {
            return "Filters products by range on " + field;
        }
        Map<String, Object> bounds = new LinkedHashMap<>();
        for (Object k : rangeValues.keySet()) {
            bounds.put(String.valueOf(k), rangeValues.get(k));
        }

        if (bounds.containsKey("lte") && bounds.size() == 1) {
            return "Filters products where " + field + " <= " + bounds.get("lte");
        }
        if (bounds.containsKey("gte") && bounds.size() == 1) {
            return "Filters products where " + field + " >= " + bounds.get("gte");
        }
        return "Filters products by range on " + field + " with " + bounds;
    }
}

