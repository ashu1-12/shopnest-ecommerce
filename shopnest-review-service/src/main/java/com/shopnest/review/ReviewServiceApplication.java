package com.shopnest.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Review Service — product ratings and moderated reviews.
 *
 * Key business rules:
 *   • Only verified purchasers can submit a review (check via order-service)
 *   • One review per product per user
 *   • Reviews go through moderation (spam/profanity filter)
 *   • Aggregate rating (avg stars, total count) cached in Redis
 *   • Helpful votes (thumbs up/down) tracked per review
 *
 * Key endpoints:
 *   POST /reviews                      — Submit review (auth required)
 *   GET  /reviews/product/{productId}  — All reviews for a product
 *   GET  /reviews/product/{productId}/summary — Avg rating + breakdown
 *   POST /reviews/{id}/helpful         — Mark review as helpful
 *   DELETE /reviews/{id}               — Delete own review
 *
 * @EnableCaching caches aggregate rating data in Redis.
 * Rating summary is recomputed and cached whenever a new review is added.
 * Cache key: "rating:product:{productId}"
 */
@SpringBootApplication
@EnableEurekaClient
@EnableJpaAuditing
@EnableKafka
@EnableCaching   // Cache aggregate ratings in Redis
public class ReviewServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}
