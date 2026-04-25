package com.example.elasticsearchdemo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.example.elasticsearchdemo.model.ImpactRow;
import com.example.elasticsearchdemo.model.QueryComponent;
import com.example.elasticsearchdemo.queryops.LeafQueryOperatorHandler;
import com.example.elasticsearchdemo.queryops.LeafQueryOperatorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryDecomposerService {

    private static final String INDEX_NAME = "products";
    private static final Logger log = LoggerFactory.getLogger(QueryDecomposerService.class);

    private final ElasticsearchClient elasticsearchClient;
    private final LeafQueryOperatorRegistry operatorRegistry;
    private final QueryComponentParser queryComponentParser;

    public QueryDecomposerService(ElasticsearchClient elasticsearchClient, LeafQueryOperatorRegistry operatorRegistry) {
        this.elasticsearchClient = elasticsearchClient;
        this.operatorRegistry = operatorRegistry;
        this.queryComponentParser = new QueryComponentParser(operatorRegistry);
    }

    /**
     * Parses the incoming JSON payload into decomposed query components (no Elasticsearch calls).
     * <p>
     * This exists primarily for tests and internal reuse; the HTTP API focuses on impact tables.
     */
    public List<QueryComponent> parseComponents(Map<String, Object> payload) {
        long startNs = System.nanoTime();
        try {
            log.info("Parse-components request received: {}", payload);
            return queryComponentParser.parse(payload);
        } finally {
            log.info("Parse-components completed in {} ms", (System.nanoTime() - startNs) / 1_000_000);
        }
    }

    /**
     * Returns a "table-like" impact view: base count, then cumulative counts after applying each component.
     */
    public List<ImpactRow> decomposeImpactTable(Map<String, Object> payload) throws IOException {
        long startNs = System.nanoTime();
        try {
            log.info("Decompose-impact-table request received: {}", payload);

            List<QueryComponent> components = queryComponentParser.parse(payload);

            List<ImpactRow> rows = new ArrayList<>();
            long base = elasticsearchClient.count(c -> c.index(INDEX_NAME).query(buildCumulativeBoolQuery(List.of(), List.of(), List.of()))).count();
            rows.add(new ImpactRow("Base query (all documents)", base, "All documents in index '" + INDEX_NAME + "'"));

            List<Query> mustQueries = new ArrayList<>();
            List<Query> filterQueries = new ArrayList<>();
            List<Query> shouldQueries = new ArrayList<>();
            List<QueryComponent> pendingShouldComponents = new ArrayList<>();

            for (QueryComponent component : components) {
                Query q = toEsQuery(component);
                String type = component.getType();

                if ("must".equals(type)) {
                    mustQueries.add(q);
                } else if ("filter".equals(type)) {
                    filterQueries.add(q);
                } else if ("should".equals(type)) {
                    shouldQueries.add(q);
                    pendingShouldComponents.add(component);
                    // Don't emit a row yet; "should" clauses are an OR group and should be shown as one step.
                    continue;
                } else {
                    mustQueries.add(q);
                }

                Query cumulativeQuery = buildCumulativeBoolQuery(mustQueries, filterQueries, shouldQueries);
                long count = elasticsearchClient.count(c -> c.index(INDEX_NAME).query(cumulativeQuery)).count();

                rows.add(new ImpactRow(labelFor(component), count, component.getExplanation()));
            }

            // Emit a single "OR group" row for any should clauses.
            if (!pendingShouldComponents.isEmpty()) {
                Query cumulativeQuery = buildCumulativeBoolQuery(mustQueries, filterQueries, shouldQueries);
                long count = elasticsearchClient.count(c -> c.index(INDEX_NAME).query(cumulativeQuery)).count();
                rows.add(new ImpactRow(labelForShouldGroup(pendingShouldComponents), count, explainShouldGroup(pendingShouldComponents)));
            }

            if (!rows.isEmpty()) {
                ImpactRow last = rows.get(rows.size() - 1);
                rows.add(new ImpactRow("Final result", last.getResultsAfterApplying(), "After all components are applied"));
            }

            return rows;
        } finally {
            log.info("Decompose-impact-table completed in {} ms", (System.nanoTime() - startNs) / 1_000_000);
        }
    }

    /**
     * Same table as {@link #decomposeImpactTable(Map)}, but computed in a single Elasticsearch request using a
     * {@code filters} aggregation (one bucket per cumulative stage).
     */
    public List<ImpactRow> decomposeImpactTableAggregations(Map<String, Object> payload) throws IOException {
        long startNs = System.nanoTime();
        try {
            log.info("Decompose-impact-table-aggregations request received: {}", payload);

            List<QueryComponent> components = queryComponentParser.parse(payload);

            List<String> stageKeys = new ArrayList<>();
            List<Query> stageQueries = new ArrayList<>();
            List<ImpactRow> stageMeta = new ArrayList<>();

            // Base stage
            stageKeys.add("stage_0_base");
            stageQueries.add(buildCumulativeBoolQuery(List.of(), List.of(), List.of()));
            stageMeta.add(new ImpactRow("Base query (all documents)", null, "All documents in index '" + INDEX_NAME + "'"));

            List<Query> mustQueries = new ArrayList<>();
            List<Query> filterQueries = new ArrayList<>();
            List<Query> shouldQueries = new ArrayList<>();
            List<QueryComponent> pendingShouldComponents = new ArrayList<>();

            int stageIdx = 1;
            for (QueryComponent component : components) {
                Query q = toEsQuery(component);
                String type = component.getType();

                if ("must".equals(type)) {
                    mustQueries.add(q);
                } else if ("filter".equals(type)) {
                    filterQueries.add(q);
                } else if ("should".equals(type)) {
                    shouldQueries.add(q);
                    pendingShouldComponents.add(component);
                    continue;
                } else {
                    mustQueries.add(q);
                }

                stageKeys.add("stage_" + stageIdx++ + "_" + safeAggKey(labelFor(component)));
                stageQueries.add(buildCumulativeBoolQuery(List.copyOf(mustQueries), List.copyOf(filterQueries), List.copyOf(shouldQueries)));
                stageMeta.add(new ImpactRow(labelFor(component), null, component.getExplanation()));
            }

            if (!pendingShouldComponents.isEmpty()) {
                stageKeys.add("stage_" + stageIdx + "_" + safeAggKey(labelForShouldGroup(pendingShouldComponents)));
                stageQueries.add(buildCumulativeBoolQuery(List.copyOf(mustQueries), List.copyOf(filterQueries), List.copyOf(shouldQueries)));
                stageMeta.add(new ImpactRow(labelForShouldGroup(pendingShouldComponents), null, explainShouldGroup(pendingShouldComponents)));
            }

            Map<String, Query> keyedFilters = new LinkedHashMap<>();
            for (int i = 0; i < stageKeys.size(); i++) {
                keyedFilters.put(stageKeys.get(i), stageQueries.get(i));
            }

            SearchResponse<Void> response = elasticsearchClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(0)
                            .aggregations("impact_table", a -> a.filters(FiltersAggregation.of(f -> f
                                    .filters(Buckets.of(b -> b.keyed(keyedFilters)))
                            )))
                    , Void.class);

            Aggregate agg = response.aggregations().get("impact_table");
            if (agg == null || !agg.isFilters()) {
                throw new IllegalStateException("Unexpected aggregation response shape for impact_table.");
            }

            List<ImpactRow> rows = new ArrayList<>();
            for (int i = 0; i < stageKeys.size(); i++) {
                String key = stageKeys.get(i);
                var bucket = agg.filters().buckets().keyed().get(key);
                long docCount = bucket == null ? 0L : bucket.docCount();
                ImpactRow template = stageMeta.get(i);
                rows.add(new ImpactRow(template.getComponent(), docCount, template.getExplanation()));
            }

            if (!rows.isEmpty()) {
                ImpactRow last = rows.get(rows.size() - 1);
                rows.add(new ImpactRow("Final result", last.getResultsAfterApplying(), "After all components are applied"));
            }

            return rows;
        } finally {
            log.info("Decompose-impact-table-aggregations completed in {} ms", (System.nanoTime() - startNs) / 1_000_000);
        }
    }

    private Query buildCumulativeBoolQuery(List<Query> mustQueries, List<Query> filterQueries, List<Query> shouldQueries) {
        if (mustQueries.isEmpty() && filterQueries.isEmpty() && shouldQueries.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        BoolQuery.Builder b = new BoolQuery.Builder();
        if (!mustQueries.isEmpty()) {
            b.must(mustQueries);
        }
        if (!filterQueries.isEmpty()) {
            b.filter(filterQueries);
        }
        if (!shouldQueries.isEmpty()) {
            b.should(shouldQueries);
            b.minimumShouldMatch("1");
        }
        return Query.of(q -> q.bool(b.build()));
    }

    private String safeAggKey(String label) {
        String s = label == null ? "unknown" : label;
        s = s.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (s.isBlank()) {
            return "x";
        }
        if (s.length() > 40) {
            return s.substring(0, 40);
        }
        return s;
    }

    private Query toEsQuery(QueryComponent component) {
        if ("bool".equals(component.getOperation())) {
            return buildBoolQuery(component.getChildren());
        }

        LeafQueryOperatorHandler handler = operatorRegistry.getOrNull(component.getOperation());
        if (handler == null) {
            return Query.of(q -> q.matchAll(m -> m));
        }
        return handler.toQuery(component);
    }

    private Query buildBoolQuery(List<QueryComponent> children) {
        if (children == null || children.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        BoolQuery.Builder b = new BoolQuery.Builder();
        for (QueryComponent child : children) {
            Query childQuery = toEsQuery(child);
            String type = child.getType();
            if ("must".equals(type)) {
                b.must(childQuery);
            } else if ("filter".equals(type)) {
                b.filter(childQuery);
            } else if ("should".equals(type)) {
                b.should(childQuery);
            } else {
                // root or unknown: treat as must
                b.must(childQuery);
            }
        }
        return Query.of(q -> q.bool(b.build()));
    }

    private String labelFor(QueryComponent c) {
        if ("range".equals(c.getOperation()) && c.getValue() instanceof Map<?, ?> rangeValues) {
            Object gte = rangeValues.get("gte");
            Object lte = rangeValues.get("lte");
            Object gt = rangeValues.get("gt");
            Object lt = rangeValues.get("lt");
            if (gte != null && lte != null) {
                return c.getField() + " range: " + gte + "–" + lte;
            }
            if (gt != null && lt != null) {
                return c.getField() + " range: (" + gt + ", " + lt + ")";
            }
            if (lte != null) {
                return c.getField() + " <= " + lte;
            }
            if (gte != null) {
                return c.getField() + " >= " + gte;
            }
        }
        if ("term".equals(c.getOperation())) {
            return c.getField() + ": " + String.valueOf(c.getValue());
        }
        if ("match".equals(c.getOperation())) {
            return c.getField() + " matches \"" + String.valueOf(c.getValue()) + "\"";
        }
        if ("bool".equals(c.getOperation())) {
            return "Nested bool";
        }
        return c.getOperation() + " on " + c.getField();
    }

    private String labelForShouldGroup(List<QueryComponent> shouldComponents) {
        // Common case: brand terms → Brand: "Sony" OR "Samsung"
        boolean allBrandTerms = shouldComponents.stream().allMatch(c ->
                "term".equals(c.getOperation()) && "brand".equals(c.getField())
        );
        if (allBrandTerms) {
            String joined = shouldComponents.stream()
                    .map(c -> "\"" + String.valueOf(c.getValue()) + "\"")
                    .distinct()
                    .reduce((a, b) -> a + " OR " + b)
                    .orElse("");
            return "Brand: " + joined;
        }

        // Fallback: operation/field summary
        String joined = shouldComponents.stream()
                .map(this::labelFor)
                .distinct()
                .reduce((a, b) -> a + " OR " + b)
                .orElse("Should group");
        return joined;
    }

    private String explainShouldGroup(List<QueryComponent> shouldComponents) {
        if (shouldComponents.isEmpty()) {
            return null;
        }
        if (shouldComponents.size() == 1) {
            return shouldComponents.get(0).getExplanation();
        }
        return "Matches documents that satisfy at least one of the OR conditions (" + shouldComponents.size() + " clause(s))";
    }
}
