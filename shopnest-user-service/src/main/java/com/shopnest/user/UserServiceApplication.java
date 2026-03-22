package com.shopnest.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * User Service — handles all authentication and user profile management.
 *
 * On startup exposes:
 *   POST /auth/register         — Create new account
 *   POST /auth/login            — Get JWT access + refresh tokens
 *   POST /auth/refresh          — Exchange refresh token for new access token
 *   POST /auth/forgot-password  — Send OTP to email
 *   POST /auth/reset-password   — Reset with OTP
 *   GET  /users/me              — Get own profile
 *   PUT  /users/me              — Update profile
 *   GET  /users/me/addresses    — Saved delivery addresses
 *
 * OAuth2 Social Login (Google):
 *   GET  /oauth2/authorization/google  → redirects to Google
 *   GET  /login/oauth2/code/google     → callback, issues our JWT
 */
@SpringBootApplication
@EnableEurekaClient
@EnableJpaAuditing
@EnableKafka
@EnableScheduling
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
