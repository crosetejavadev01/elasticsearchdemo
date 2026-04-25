package com.example.elasticsearchdemo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryComponent {
    private String type;
    private String operation;
    private String field;
    private Object value;
    private Long resultCount;
    private String explanation;
    private List<QueryComponent> children;

    public QueryComponent(String type, String operation, String field, Object value) {
        this.type = type;
        this.operation = operation;
        this.field = field;
        this.value = value;
    }
}
