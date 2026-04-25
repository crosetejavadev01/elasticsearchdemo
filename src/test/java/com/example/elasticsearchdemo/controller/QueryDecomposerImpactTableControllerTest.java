package com.example.elasticsearchdemo.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.FiltersBucket;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.example.elasticsearchdemo.exception.ApiExceptionHandler;
import com.example.elasticsearchdemo.service.QueryDecomposerService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QueryDecomposerController.class)
@Import({
        QueryDecomposerService.class,
        com.example.elasticsearchdemo.queryops.LeafQueryOperatorRegistry.class,
        com.example.elasticsearchdemo.queryops.MatchOperatorHandler.class,
        com.example.elasticsearchdemo.queryops.TermOperatorHandler.class,
        com.example.elasticsearchdemo.queryops.RangeOperatorHandler.class,
        ApiExceptionHandler.class
})
class QueryDecomposerImpactTableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ElasticsearchClient elasticsearchClient;

    @Test
    void decomposeImpactTable_shouldReturnProgressiveRows_optionA() throws Exception {
        CountResponse countResponse = Mockito.mock(CountResponse.class);
        when(elasticsearchClient.count(any(Function.class))).thenReturn(countResponse);
        // Base + 4 stages (match, price, inStock, rating) + should-group stage
        when(countResponse.count()).thenReturn(50_000L, 10_086L, 704L, 514L, 240L, 49L);

        String payload = """
                {
                  "bool": {
                    "must": [
                      { "match": { "category": "electronics" } }
                    ],
                    "filter": [
                      { "range": { "price": { "gte": 50, "lte": 200 } } },
                      { "term": { "inStock": true } },
                      { "range": { "rating": { "gte": 4 } } }
                    ],
                    "should": [
                      { "term": { "brand": "Sony" } },
                      { "term": { "brand": "Samsung" } }
                    ]
                  }
                }
                """;

        mockMvc.perform(post("/api/decompose-impact-table")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].component").value("Base query (all documents)"))
                .andExpect(jsonPath("$[0].resultsAfterApplying").value(50000))
                .andExpect(jsonPath("$[1].resultsAfterApplying").value(10086))
                .andExpect(jsonPath("$[2].resultsAfterApplying").value(704))
                .andExpect(jsonPath("$[3].resultsAfterApplying").value(514))
                .andExpect(jsonPath("$[4].resultsAfterApplying").value(240))
                .andExpect(jsonPath("$[5].resultsAfterApplying").value(49))
                .andExpect(jsonPath("$[6].component").value("Final result"))
                .andExpect(jsonPath("$[6].resultsAfterApplying").value(49));
    }

    @Test
    void decomposeImpactTableAggregations_shouldReturnProgressiveRows_optionB() throws Exception {
        @SuppressWarnings("unchecked")
        SearchResponse<Void> response = Mockito.mock(SearchResponse.class);
        Aggregate agg = Mockito.mock(Aggregate.class);
        FiltersAggregate filtersAggregate = Mockito.mock(FiltersAggregate.class);
        @SuppressWarnings("unchecked")
        Buckets<FiltersBucket> buckets = Mockito.mock(Buckets.class);
        FiltersBucket b0 = Mockito.mock(FiltersBucket.class);
        FiltersBucket b1 = Mockito.mock(FiltersBucket.class);
        FiltersBucket b2 = Mockito.mock(FiltersBucket.class);
        FiltersBucket b3 = Mockito.mock(FiltersBucket.class);
        FiltersBucket b4 = Mockito.mock(FiltersBucket.class);
        FiltersBucket b5 = Mockito.mock(FiltersBucket.class);

        Map<String, FiltersBucket> keyed = new LinkedHashMap<>();
        keyed.put("stage_0_base", b0);
        keyed.put("stage_1_category_matches_electronics", b1);
        keyed.put("stage_2_price_range_50_200", b2);
        keyed.put("stage_3_instock_true", b3);
        keyed.put("stage_4_rating_4", b4);
        keyed.put("stage_5_brand_sony_or_samsung", b5);

        when(b0.docCount()).thenReturn(50_000L);
        when(b1.docCount()).thenReturn(10_086L);
        when(b2.docCount()).thenReturn(704L);
        when(b3.docCount()).thenReturn(514L);
        when(b4.docCount()).thenReturn(240L);
        when(b5.docCount()).thenReturn(49L);

        when(elasticsearchClient.search(any(Function.class), eq(Void.class))).thenReturn(response);
        when(response.aggregations()).thenReturn(Map.of("impact_table", agg));
        when(agg.isFilters()).thenReturn(true);
        when(agg.filters()).thenReturn(filtersAggregate);
        when(filtersAggregate.buckets()).thenReturn(buckets);
        when(buckets.keyed()).thenReturn(keyed);

        String payload = """
                {
                  "bool": {
                    "must": [
                      { "match": { "category": "electronics" } }
                    ],
                    "filter": [
                      { "range": { "price": { "gte": 50, "lte": 200 } } },
                      { "term": { "inStock": true } },
                      { "range": { "rating": { "gte": 4 } } }
                    ],
                    "should": [
                      { "term": { "brand": "Sony" } },
                      { "term": { "brand": "Samsung" } }
                    ]
                  }
                }
                """;

        mockMvc.perform(post("/api/decompose-impact-table-aggregations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].component").value("Base query (all documents)"))
                .andExpect(jsonPath("$[0].resultsAfterApplying").value(50000))
                .andExpect(jsonPath("$[5].resultsAfterApplying").value(49))
                .andExpect(jsonPath("$[6].component").value("Final result"))
                .andExpect(jsonPath("$[6].resultsAfterApplying").value(49));
    }
}

