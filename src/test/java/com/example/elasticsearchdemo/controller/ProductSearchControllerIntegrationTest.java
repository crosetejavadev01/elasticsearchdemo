package com.example.elasticsearchdemo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductSearchControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchProducts_shouldReturnElectronicsWithinRange() throws Exception {
        String payload = """
                {
                  "bool": {
                    "must": [
                      { "match": { "category": "electronics" } }
                    ],
                    "filter": [
                      { "range": { "price": { "lte": 30000 } } }
                    ]
                  }
                }
                """;

        mockMvc.perform(post("/api/search-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Headphones"))
                .andExpect(jsonPath("$[0].category").value("electronics"));
    }
}
