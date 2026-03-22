package com.shopnest.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Inventory Service — stock management with atomic reservation.
 *
 * Critical Design: Redis DECR for atomic stock check-and-reserve.
 *
 * Problem without atomic ops:
 *   Thread A: reads stock=1 → decides to reserve → (context switch)
 *   Thread B: reads stock=1 → decides to reserve → reserves → stock=0
 *   Thread A: resumes → reserves → stock=-1 (OVERSELL!)
 *
 * Solution with Redis DECR:
 *   Redis DECR is a single atomic command.
 *   If DECR returns >= 0 → reservation successful.
 *   If DECR returns < 0  → out of stock, INCR back to undo.
 *   No race condition possible — Redis is single-threaded.
 *
 * Key endpoints:
 *   GET  /inventory/{productId}         — Current stock level
 *   POST /inventory/reserve             — Atomic stock reservation
 *   POST /inventory/release             — Release held reservation
 *   POST /inventory/confirm             — Confirm deduction (after payment)
 *   PUT  /inventory/{productId}/restock — Add new stock (admin/seller)
 */
@SpringBootApplication
@EnableEurekaClient
@EnableJpaAuditing
@EnableKafka
@EnableScheduling   // For periodic stock sync job (DB → Redis)
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
