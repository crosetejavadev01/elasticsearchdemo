package com.example.elasticsearchdemo.controller;

import com.example.elasticsearchdemo.model.ImpactRow;
import com.example.elasticsearchdemo.service.QueryDecomposerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryDecomposerController {

    private final QueryDecomposerService queryDecomposerService;

    public QueryDecomposerController(QueryDecomposerService queryDecomposerService) {
        this.queryDecomposerService = queryDecomposerService;
    }

    @PostMapping(value = "/decompose-impact-table", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ImpactRow> decomposeImpactTable(@RequestBody Map<String, Object> payload) throws IOException {
        return queryDecomposerService.decomposeImpactTable(payload);
    }

    @PostMapping(value = "/decompose-impact-table-aggregations", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ImpactRow> decomposeImpactTableAggregations(@RequestBody Map<String, Object> payload) throws IOException {
        return queryDecomposerService.decomposeImpactTableAggregations(payload);
    }
}
