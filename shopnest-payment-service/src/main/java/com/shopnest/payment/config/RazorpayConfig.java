package com.shopnest.payment.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * ┌───────────────────────────────────────────────────────────────┐
 * │  RAZORPAY CONFIGURATION                                       │
 * │                                                               │
 * │  Manages the Razorpay API client lifecycle.                   │
 * │                                                               │
 * │  Keys needed (get from Razorpay Dashboard → Settings → Keys): │
 * │    key_id:     rzp_test_XXXXXXXXXX  (public — safe to expose) │
 * │    key_secret: XXXXXXXXXXXXXXXXX   (private — NEVER expose)   │
 * │                                                               │
 * │  For development: use test keys (rzp_test_...)                │
 * │  For production:  use live keys  (rzp_live_...)               │
 * │                                                               │
 * │  Keys loaded from environment variables / application.yml     │
 * │  NEVER hardcode them in source code!                          │
 * └───────────────────────────────────────────────────────────────┘
 */
@Configuration
@Slf4j
@Getter
public class RazorpayConfig {

    @Value("${razorpay.key-id}")
    private String keyId;        // Public key — sent to frontend for Razorpay.js

    @Value("${razorpay.secret-key}")
    private String secretKey;    // Private key — only used server-side

    /**
     * Returns a RazorpayClient instance.
     *
     * Why not @Bean singleton?
     * RazorpayClient is not thread-safe in older SDK versions.
     * Creating a new instance per call is safer (client is lightweight).
     *
     * For high-traffic production systems, consider connection pooling
     * or upgrading to the latest Razorpay SDK which may be thread-safe.
     */
    public RazorpayClient getRazorpayClient() {
        try {
            return new RazorpayClient(keyId, secretKey);
        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay client: {}", e.getMessage());
            throw new RuntimeException("Razorpay client initialization failed", e);
        }
    }
}
