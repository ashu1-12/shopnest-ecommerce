package com.shopnest.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Order Service — shopping cart, checkout, and order lifecycle.
 *
 * Key endpoints:
 *   POST   /cart/items              — Add item to cart
 *   PUT    /cart/items/{itemId}     — Update quantity
 *   DELETE /cart/items/{itemId}     — Remove item
 *   GET    /cart                    — View cart with live prices
 *   POST   /orders                  — Place order (triggers payment flow)
 *   GET    /orders                  — Order history
 *   GET    /orders/{id}             — Order detail + tracking
 *   POST   /orders/{id}/cancel      — Cancel order
 *   POST   /orders/{id}/return      — Initiate return
 *
 * @EnableFeignClients scans for interfaces annotated with @FeignClient.
 * Feign turns them into HTTP clients automatically. Example:
 *
 *   @FeignClient(name = "SHOPNEST-INVENTORY-SERVICE")
 *   public interface InventoryClient {
 *       @PostMapping("/inventory/reserve")
 *       StockReservationResponse reserveStock(@RequestBody ReserveRequest req);
 *   }
 *
 * This lets OrderService call InventoryService as if it's a local method.
 * Eureka handles the actual host/port resolution.
 */
@SpringBootApplication
@EnableEurekaClient
@EnableJpaAuditing
@EnableKafka
@EnableScheduling
@EnableCaching
@EnableFeignClients   // Enables declarative REST clients to inventory + shipping services
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
