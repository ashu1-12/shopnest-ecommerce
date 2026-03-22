package com.shopnest.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * Config Server — centralized configuration for all microservices.
 *
 * How it works:
 *   1. Config Server reads from a Git repository (or local folder)
 *   2. Each service calls Config Server on startup:
 *      GET http://config-server:8888/{service-name}/{profile}
 *   3. Config Server returns the YAML/properties for that service
 *   4. Service merges these properties with its local application.yml
 *
 * Git repo structure (example):
 *   config-repo/
 *     application.yml                    ← common to ALL services
 *     shopnest-payment-service.yml       ← payment-specific config
 *     shopnest-user-service.yml          ← user-specific config
 *     shopnest-payment-service-prod.yml  ← production overrides
 *
 * Secrets management:
 *   Use {cipher}... prefix in config files for encrypted values.
 *   Config Server decrypts them before serving to clients.
 *   Encryption key stored in ENCRYPT_KEY environment variable.
 */
@SpringBootApplication
@EnableConfigServer    // Turns this into a Spring Cloud Config Server
@EnableEurekaClient    // Registers config server in Eureka too
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
