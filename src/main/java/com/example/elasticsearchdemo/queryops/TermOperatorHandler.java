package com.example.elasticsearchdemo.queryops;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.elasticsearchdemo.model.QueryComponent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TermOperatorHandler implements LeafQueryOperatorHandler {

    @Override
    public String operator() {
        return "term";
    }

    @Override
    public void validate(Object operatorPayload) {
        if (!(operatorPayload instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Invalid 'term' operation: expected object.");
        }
    }

    @Override
    public List<Query> toQueries(Object operatorPayload) {
        if (!(operatorPayload instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<Query> queries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String field = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            queries.add(Query.of(q -> q.term(t -> t.field(field).value(toFieldValue(value)))));
        }
        return queries;
    }

    @Override
    public List<QueryComponent> toComponents(String clauseType, Object operatorPayload) {
        if (!(operatorPayload instanceof Map<?, ?> map)) {
            return List.of();
        }
        List<QueryComponent> out = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String field = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            QueryComponent c = new QueryComponent(clauseType, "term", field, value);
            c.setExplanation("Filters products where " + field + " equals " + String.valueOf(value));
            out.add(c);
        }
        return out;
    }

    @Override
    public Query toQuery(QueryComponent component) {
        return Query.of(q -> q.term(t -> t.field(component.getField()).value(toFieldValue(component.getValue()))));
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

