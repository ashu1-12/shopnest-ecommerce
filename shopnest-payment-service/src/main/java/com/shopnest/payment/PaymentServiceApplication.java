package com.shopnest.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ┌──────────────────────────────────────────────────────────────┐
 * │         PAYMENT SERVICE — ENTRY POINT                        │
 * │                                                              │
 * │  Annotations explained:                                      │
 * │                                                              │
 * │  @SpringBootApplication                                      │
 * │    = auto-configures everything Spring sees on classpath     │
 * │    = scans all @Component/@Service/@Repository in package    │
 * │                                                              │
 * │  @EnableEurekaClient                                         │
 * │    = registers this service in Eureka on startup             │
 * │    = other services can discover us by name                  │
 * │                                                              │
 * │  @EnableJpaAuditing                                          │
 * │    = activates @CreatedDate / @LastModifiedDate in entities  │
 * │    = auto-fills timestamps without manual code               │
 * │                                                              │
 * │  @EnableKafka                                                │
 * │    = activates @KafkaListener annotation support             │
 * │                                                              │
 * │  @EnableScheduling                                           │
 * │    = activates @Scheduled annotation                         │
 * │    = used by the stale payment cleanup job                   │
 * └──────────────────────────────────────────────────────────────┘
 */
@SpringBootApplication
@EnableEurekaClient
@EnableJpaAuditing       // Required for @CreatedDate/@LastModifiedDate in Payment entity
@EnableKafka             // Required for Kafka producers/consumers
@EnableScheduling        // Required for scheduled jobs (stale payment cleanup)
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
        // After this line, the service:
        //  1. Connects to MySQL and creates/updates the 'payments' table
        //  2. Connects to Redis
        //  3. Connects to Kafka brokers
        //  4. Registers with Eureka (takes ~30s to become visible)
        //  5. Starts listening on port 8084
        //  6. Swagger UI available at http://localhost:8084/swagger-ui.html
    }
}
