package com.shopnest.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Product Service — catalog management and Elasticsearch-powered search.
 *
 * Key endpoints:
 *   GET  /products                    — List with filters & pagination
 *   GET  /products/{id}               — Single product detail
 *   GET  /products/search?q=iphone    — Full-text Elasticsearch search
 *   POST /products                    — Create product (SELLER/ADMIN only)
 *   PUT  /products/{id}               — Update product
 *   GET  /categories                  — Category tree
 *   POST /products/{id}/images        — Upload product images to S3
 *
 * @EnableCaching activates Spring's @Cacheable/@CacheEvict annotations.
 * Popular product pages are cached in Redis to reduce DB load.
 * Cache TTL: 10 minutes (product details change infrequently).
 */
@SpringBootApplication
@EnableEurekaClient
@EnableJpaAuditing
@EnableKafka
@EnableCaching   // Enables @Cacheable on ProductService methods → Redis cache
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
