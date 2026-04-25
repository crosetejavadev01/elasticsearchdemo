package com.example.elasticsearchdemo.queryops;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.elasticsearchdemo.model.QueryComponent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MatchOperatorHandler implements LeafQueryOperatorHandler {

    @Override
    public String operator() {
        return "match";
    }

    @Override
    public void validate(Object operatorPayload) {
        if (!(operatorPayload instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Invalid 'match' operation: expected object.");
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
            String value = String.valueOf(entry.getValue());
            queries.add(Query.of(q -> q.match(m -> m.field(field).query(value))));
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
            QueryComponent c = new QueryComponent(clauseType, "match", field, value);
            c.setExplanation("Matches products where " + field + " matches \"" + String.valueOf(value) + "\"");
            out.add(c);
        }
        return out;
    }

    @Override
    public Query toQuery(QueryComponent component) {
        return Query.of(q -> q.match(m -> m.field(component.getField()).query(String.valueOf(component.getValue()))));
    }
}

