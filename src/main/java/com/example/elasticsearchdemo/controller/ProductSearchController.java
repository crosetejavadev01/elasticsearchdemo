package com.example.elasticsearchdemo.controller;

import com.example.elasticsearchdemo.model.Product;
import com.example.elasticsearchdemo.service.ProductSearchService;
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
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    public ProductSearchController(ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
    }

    @PostMapping(value = "/search-products", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Product> searchProducts(@RequestBody Map<String, Object> payload) throws IOException {
        return productSearchService.search(payload);
    }
}
