package com.shopnest.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * API Gateway — the single entry point for the entire ShopNest platform.
 *
 * All traffic flows:
 *   Internet → API Gateway (port 8080)
 *              → Routes to appropriate microservice via Eureka
 *
 * Key features active at startup:
 *   • JWT validation on all protected routes
 *   • Rate limiting per user/IP via Redis
 *   • Circuit breakers via Resilience4J
 *   • CORS headers for React frontend
 *   • Request logging with correlation IDs
 *
 * Note: Uses Spring WebFlux (reactive/non-blocking).
 * Do NOT mix with Spring MVC (spring-boot-starter-web) in this module!
 */
@SpringBootApplication
@EnableEurekaClient
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
