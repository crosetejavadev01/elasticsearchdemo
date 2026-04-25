package com.example.elasticsearchdemo.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ElasticsearchHealthController {

    private final ElasticsearchClient elasticsearchClient;

    public ElasticsearchHealthController(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @GetMapping("/ping-es")
    public Map<String, Object> pingEs() throws IOException {
        InfoResponse info = elasticsearchClient.info();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clusterName", info.clusterName());
        response.put("version", info.version().number());
        response.put("tagline", info.tagline());
        return response;
    }
}
