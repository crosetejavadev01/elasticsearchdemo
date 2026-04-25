package com.example.elasticsearchdemo.queryops;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.elasticsearchdemo.model.QueryComponent;

import java.util.List;

/**
 * Strategy for handling a single non-bool operator (e.g. match/term/range).
 * Adding support for a new operator should only require implementing this interface
 * and registering it in {@link LeafQueryOperatorRegistry}.
 */
public interface LeafQueryOperatorHandler {

    String operator();

    /**
     * Validates the operator payload object (e.g. the value under "term" or "range").
     * Implementations should throw {@link IllegalArgumentException} for invalid shapes.
     */
    void validate(Object operatorPayload);

    /**
     * Converts the operator payload to one or more Elasticsearch queries.
     */
    List<Query> toQueries(Object operatorPayload);

    /**
     * Converts the operator payload into decomposed components.
     */
    List<QueryComponent> toComponents(String clauseType, Object operatorPayload);

    /**
     * Builds an Elasticsearch query from a previously decomposed component.
     */
    Query toQuery(QueryComponent component);
}

