package com.example.elasticsearchdemo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI elasticsearchDemoOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Elasticsearch Demo API")
                        .version("v1")
                        .description("Product search + query decomposition utilities backed by Elasticsearch."));
    }
}
