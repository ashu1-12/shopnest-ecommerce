package com.shopnest.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * ┌─────────────────────────────────────────────────────────────────┐
 * │           GATEWAY CONFIGURATION                                 │
 * │                                                                 │
 * │  Defines Spring Beans used by the gateway:                      │
 * │    1. userKeyResolver  — rate limit by user ID (from JWT)       │
 * │    2. ipKeyResolver    — rate limit by IP (for public routes)   │
 * │                                                                 │
 * │  @Configuration = declares this class as a source of @Bean      │
 * └─────────────────────────────────────────────────────────────────┘
 */
@Configuration
public class GatewayConfig {

    /**
     * Rate Limiter Key Resolver — "Who is making this request?"
     *
     * The gateway rate limiter needs a "key" to identify each user.
     * We use the X-User-Id header that our AuthenticationFilter adds.
     *
     * This means:
     *   - Each USER gets their own rate limit bucket (not shared)
     *   - User A can't exhaust User B's quota
     *   - Unauthenticated requests fall back to IP address
     *
     * Referenced in application.yml as: key-resolver: "#{@userKeyResolver}"
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return (ServerWebExchange exchange) -> {
            // Try to get userId from header (set by AuthenticationFilter)
            String userId = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-User-Id");

            if (userId != null) {
                // Authenticated user — rate limit per userId
                return Mono.just("user:" + userId);
            }

            // Unauthenticated — rate limit per IP address
            // getRemoteAddress() can be null in some proxy setups
            String ip = exchange.getRequest()
                    .getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";

            return Mono.just("ip:" + ip);
        };
    }
}
