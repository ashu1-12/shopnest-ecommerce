package com.shopnest.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * ┌──────────────────────────────────────────────────────────────────┐
 * │           JWT AUTHENTICATION FILTER                              │
 * │                                                                  │
 * │  This filter runs BEFORE every protected route request.          │
 * │                                                                  │
 * │  Flow:                                                           │
 * │  1. Request arrives at gateway                                   │
 * │  2. Filter checks Authorization header for "Bearer <token>"     │
 * │  3. Validates the JWT signature and expiry                       │
 * │  4. If valid: extract userId, role → add to request headers      │
 * │     Then forward to the actual service                           │
 * │  5. If invalid: return 401 Unauthorized immediately             │
 * │                                                                  │
 * │  Why validate at gateway level?                                  │
 * │  → Each service doesn't need its own auth logic                  │
 * │  → Single point of security enforcement                          │
 * │  → Services can trust the X-User-Id header the gateway adds      │
 * └──────────────────────────────────────────────────────────────────┘
 */
@Component  // Spring Bean — auto-detected
@Slf4j      // Lombok: injects 'log' field — replaces LoggerFactory boilerplate
public class AuthenticationFilter
        extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // AbstractGatewayFilterFactory requires a Config class (can be empty)
    public AuthenticationFilter() {
        super(Config.class);
    }

    /**
     * This is the main filter method.
     * It returns a GatewayFilter (a lambda) that runs on every matched request.
     */
    @Override
    public GatewayFilter apply(Config config) {

        // GatewayFilter = functional interface with (exchange, chain) args
        // exchange = the HTTP request + response wrapper
        // chain = the next filter in the pipeline
        return (exchange, chain) -> {

            // ── Step 1: Extract Authorization header ──────────────
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            // ── Step 2: Validate header format ───────────────────
            // Must be: "Bearer eyJhbGciOiJIUzI1NiIsInR5c..."
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Request rejected — missing or malformed Authorization header");
                return unauthorizedResponse(exchange, "Missing Authorization header");
            }

            // ── Step 3: Extract just the token (remove "Bearer " prefix) ─
            String token = authHeader.substring(7);

            try {
                // ── Step 4: Parse and validate the JWT ────────────
                // This throws an exception if:
                //   - Token is expired
                //   - Token signature is wrong (someone tampered with it)
                //   - Token is malformed
                Claims claims = Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(
                                jwtSecret.getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // ── Step 5: Extract user info from token payload ──
                String userId = claims.getSubject();          // User ID stored as 'sub'
                String role = claims.get("role", String.class); // e.g., "CUSTOMER", "ADMIN"
                String email = claims.get("email", String.class);

                log.debug("JWT validated for userId={}, role={}", userId, role);

                // ── Step 6: Forward user info as headers to services ──
                // Downstream services (order-service, payment-service) read these
                // headers to know WHO is making the request.
                // They don't need to re-validate the JWT themselves.
                ServerWebExchange mutatedExchange = exchange.mutate()
                        .request(exchange.getRequest().mutate()
                                .header("X-User-Id", userId)
                                .header("X-User-Role", role)
                                .header("X-User-Email", email)
                                .build())
                        .build();

                // ── Step 7: Continue to next filter / service ─────
                return chain.filter(mutatedExchange);

            } catch (Exception e) {
                // Catch all JWT validation failures
                log.warn("JWT validation failed: {}", e.getMessage());
                return unauthorizedResponse(exchange, "Invalid or expired token");
            }
        };
    }

    /**
     * Helper: writes a 401 Unauthorized response and STOPS the filter chain.
     * Mono.empty() = reactive "done, don't continue"
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders()
                .add("Content-Type", "application/json");

        var body = String.format("{\"error\": \"%s\", \"status\": 401}", message);
        var buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Config class — required by AbstractGatewayFilterFactory.
     * Add configuration fields here if needed (e.g., excluded paths).
     */
    public static class Config {
        // Empty for now — extend if you need per-route filter configuration
    }
}
