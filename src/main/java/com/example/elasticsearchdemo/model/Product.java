package com.example.elasticsearchdemo.model;

import lombok.Data;

@Data
public class Product {
    private String name;
    private String category;
    private String brand;
    private Double price;
    private Boolean inStock;
    private Double rating;
}
