package com.example.elasticsearchdemo.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class ProductIndexInitializer implements CommandLineRunner {

    private static final String INDEX_NAME = "products";
    private static final Logger log = LoggerFactory.getLogger(ProductIndexInitializer.class);

    private final ElasticsearchClient elasticsearchClient;
    private final int seedCount;

    public ProductIndexInitializer(
            ElasticsearchClient elasticsearchClient,
            @Value("${demo.seed.count:3}") int seedCount
    ) {
        this.elasticsearchClient = elasticsearchClient;
        this.seedCount = seedCount;
    }

    @Override
    public void run(String... args) throws Exception {
        BooleanResponse exists = elasticsearchClient.indices().exists(e -> e.index(INDEX_NAME));
        if (exists.value()) {
            return;
        }

        elasticsearchClient.indices().create(c -> c
                .index(INDEX_NAME)
                .mappings(m -> m
                        .properties("name", p -> p.text(t -> t))
                        .properties("category", p -> p.keyword(k -> k))
                        .properties("brand", p -> p.keyword(k -> k))
                        .properties("price", p -> p.double_(d -> d))
                        .properties("inStock", p -> p.boolean_(b -> b))
                        .properties("rating", p -> p.double_(d -> d))
                )
        );

        seedProducts(seedCount);

        elasticsearchClient.indices().refresh(r -> r.index(INDEX_NAME));
    }

    private void seedProducts(int count) throws Exception {
        // Deterministic dataset so demos are repeatable.
        Random rnd = new Random(42);

        String[] categories = {"electronics", "furniture", "sports", "kitchen", "books"};
        String[] electronics = {"Laptop", "Headphones", "TV", "Camera", "Phone", "Tablet", "Speaker"};
        String[] furniture = {"Chair", "Desk", "Sofa", "Table", "Shelf"};
        String[] sports = {"Running Shoes", "Tennis Racket", "Football", "Dumbbells", "Bike Helmet"};
        String[] kitchen = {"Blender", "Toaster", "Coffee Maker", "Knife Set", "Air Fryer"};
        String[] books = {"Novel", "Cookbook", "Biography", "Textbook", "Comics"};
        String[] brands = {"Sony", "Samsung", "Apple", "Dell", "HP", "Ikea", "Philips", "Bose", "Adidas", "Nike"};

        int batchSize = 1000;
        List<BulkOperation> ops = new ArrayList<>(batchSize);

        long startNs = System.nanoTime();
        log.info("Seeding {} product docs into index '{}'", count, INDEX_NAME);

        for (int i = 1; i <= count; i++) {
            String category = categories[rnd.nextInt(categories.length)];
            String name = switch (category) {
                case "electronics" -> electronics[rnd.nextInt(electronics.length)];
                case "furniture" -> furniture[rnd.nextInt(furniture.length)];
                case "sports" -> sports[rnd.nextInt(sports.length)];
                case "kitchen" -> kitchen[rnd.nextInt(kitchen.length)];
                case "books" -> books[rnd.nextInt(books.length)];
                default -> "Product";
            };

            String brand = brands[rnd.nextInt(brands.length)];
            boolean inStock = rnd.nextDouble() < 0.75; // ~75% in stock

            // Price distribution by category (roughly realistic ranges)
            double price = switch (category) {
                case "electronics" -> 50 + rnd.nextDouble() * 2000;
                case "furniture" -> 30 + rnd.nextDouble() * 800;
                case "sports" -> 10 + rnd.nextDouble() * 500;
                case "kitchen" -> 10 + rnd.nextDouble() * 400;
                case "books" -> 5 + rnd.nextDouble() * 60;
                default -> 1 + rnd.nextDouble() * 100;
            };

            // Rating 1.0 - 5.0 (biased toward higher ratings)
            double rating = Math.min(5.0, 1.0 + Math.pow(rnd.nextDouble(), 0.5) * 4.0);

            String docName = brand + " " + name + " " + i;

            Map<String, Object> doc = Map.of(
                    "name", docName,
                    "category", category,
                    "brand", brand,
                    "price", Math.round(price * 100.0) / 100.0,
                    "inStock", inStock,
                    "rating", Math.round(rating * 10.0) / 10.0
            );

            final String id = String.valueOf(i);
            ops.add(BulkOperation.of(b -> b.index(idx -> idx.index(INDEX_NAME).id(id).document(doc))));

            if (ops.size() >= batchSize) {
                flushBulk(ops);
                ops.clear();
            }
        }

        if (!ops.isEmpty()) {
            flushBulk(ops);
        }

        log.info("Seeding completed in {} ms", (System.nanoTime() - startNs) / 1_000_000);
    }

    private void flushBulk(List<BulkOperation> ops) throws Exception {
        BulkRequest request = BulkRequest.of(b -> b.operations(ops));
        var resp = elasticsearchClient.bulk(request);
        if (resp.errors()) {
            throw new IllegalStateException("Bulk indexing reported errors while seeding data.");
        }
    }
}
