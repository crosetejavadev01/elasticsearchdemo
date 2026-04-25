package com.example.elasticsearchdemo.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductSearchServiceValidationTest {

    private final ProductSearchService service = new ProductSearchService(null, new com.example.elasticsearchdemo.queryops.LeafQueryOperatorRegistry(
            java.util.List.of(
                    new com.example.elasticsearchdemo.queryops.MatchOperatorHandler(),
                    new com.example.elasticsearchdemo.queryops.TermOperatorHandler(),
                    new com.example.elasticsearchdemo.queryops.RangeOperatorHandler()
            )
    ));

    @Test
    void validatePayload_shouldPassForValidBoolPayload() {
        Map<String, Object> payload = Map.of(
                "bool", Map.of(
                        "must", List.of(Map.of("match", Map.of("category", "electronics"))),
                        "filter", List.of(Map.of("range", Map.of("price", Map.of("lte", 30000))))
                )
        );

        assertDoesNotThrow(() -> service.validatePayload(payload));
    }

    @Test
    void validatePayload_shouldRejectUnsupportedOperation() {
        Map<String, Object> payload = Map.of(
                "bool", Map.of(
                        "must", List.of(Map.of("wildcard", Map.of("name", "Lap*")))
                )
        );

        assertThrows(IllegalArgumentException.class, () -> service.validatePayload(payload));
    }

    @Test
    void validatePayload_shouldRejectMissingBoolObject() {
        Map<String, Object> payload = Map.of("query", Map.of("match_all", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.validatePayload(payload));
    }
}
