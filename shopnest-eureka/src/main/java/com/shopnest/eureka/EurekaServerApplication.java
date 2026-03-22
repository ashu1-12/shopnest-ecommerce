package com.shopnest.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * ┌─────────────────────────────────────────────────────────────┐
 * │           EUREKA SERVER — ENTRY POINT                       │
 * │                                                             │
 * │  @SpringBootApplication = @Configuration +                  │
 * │                            @EnableAutoConfiguration +       │
 * │                            @ComponentScan                   │
 * │                                                             │
 * │  @EnableEurekaServer = Activates the Eureka server UI       │
 * │  and REST API at http://localhost:8761                       │
 * │                                                             │
 * │  Once running, visit: http://localhost:8761                  │
 * │  You'll see a dashboard showing all registered services.    │
 * └─────────────────────────────────────────────────────────────┘
 */
@SpringBootApplication
@EnableEurekaServer  // This single annotation = full service registry
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
