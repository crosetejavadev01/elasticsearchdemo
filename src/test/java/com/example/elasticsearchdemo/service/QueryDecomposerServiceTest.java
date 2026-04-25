package com.example.elasticsearchdemo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.elasticsearchdemo.model.QueryComponent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryDecomposerServiceTest {

    private final ElasticsearchClient elasticsearchClient = Mockito.mock(ElasticsearchClient.class);
    private final QueryDecomposerService queryDecomposerService = new QueryDecomposerService(
            elasticsearchClient,
            new com.example.elasticsearchdemo.queryops.LeafQueryOperatorRegistry(
                    java.util.List.of(
                            new com.example.elasticsearchdemo.queryops.MatchOperatorHandler(),
                            new com.example.elasticsearchdemo.queryops.TermOperatorHandler(),
                            new com.example.elasticsearchdemo.queryops.RangeOperatorHandler()
                    )
            )
    );

    @Test
    void decompose_shouldFlattenBoolPayload() {
        Map<String, Object> payload = Map.of(
                "bool", Map.of(
                        "must", List.of(Map.of("match", Map.of("category", "electronics"))),
                        "filter", List.of(Map.of("range", Map.of("price", Map.of("lte", 30000))))
                )
        );

        List<QueryComponent> result = queryDecomposerService.parseComponents(payload);

        assertEquals(2, result.size());
        assertEquals("must", result.get(0).getType());
        assertEquals("match", result.get(0).getOperation());
        assertEquals("category", result.get(0).getField());
        assertEquals("electronics", result.get(0).getValue());

        assertEquals("filter", result.get(1).getType());
        assertEquals("range", result.get(1).getOperation());
        assertEquals("price", result.get(1).getField());
        assertEquals(null, result.get(0).getResultCount());
        assertEquals(null, result.get(1).getResultCount());
    }

    @Test
    void decompose_shouldSupportQueryWrapperPayload() {
        Map<String, Object> payload = Map.of(
                "query", Map.of(
                        "bool", Map.of(
                                "should", List.of(Map.of("term", Map.of("inStock", true)))
                        )
                )
        );

        List<QueryComponent> result = queryDecomposerService.parseComponents(payload);

        assertEquals(1, result.size());
        assertEquals("should", result.get(0).getType());
        assertEquals("term", result.get(0).getOperation());
        assertEquals("inStock", result.get(0).getField());
        assertEquals(true, result.get(0).getValue());
        assertEquals(null, result.get(0).getResultCount());
    }
}
