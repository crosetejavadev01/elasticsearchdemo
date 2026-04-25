package com.example.elasticsearchdemo.queryops;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LeafQueryOperatorRegistry {

    private final Map<String, LeafQueryOperatorHandler> handlers;

    public LeafQueryOperatorRegistry(List<LeafQueryOperatorHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(LeafQueryOperatorHandler::operator, Function.identity()));
    }

    public LeafQueryOperatorHandler getOrNull(String operator) {
        return handlers.get(operator);
    }
}

