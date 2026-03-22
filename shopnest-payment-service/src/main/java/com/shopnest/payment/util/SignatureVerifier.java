package com.shopnest.payment.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  SIGNATURE VERIFIER — PAYMENT SECURITY CRITICAL COMPONENT        │
 * │                                                                  │
 * │  What is HMAC-SHA256?                                            │
 * │  HMAC = Hash-based Message Authentication Code                   │
 * │  SHA256 = the hashing algorithm                                  │
 * │                                                                  │
 * │  How it proves payment authenticity:                             │
 * │  1. Razorpay computes:                                           │
 * │     signature = HMAC_SHA256(                                     │
 * │       key    = your_razorpay_secret,                             │
 * │       message= razorpay_order_id + "|" + razorpay_payment_id    │
 * │     )                                                            │
 * │  2. Sends this signature to your frontend in success callback    │
 * │  3. You recompute the same signature on your server              │
 * │  4. If both match → payment is genuine                           │
 * │  5. If they don't match → someone tampered with the data        │
 * │                                                                  │
 * │  Why can't attackers fake this?                                  │
 * │  Because they don't know your razorpay_secret_key.               │
 * │  It never leaves your server.                                    │
 * └──────────────────────────────────────────────────────────────────┘
 */
@Component
@Slf4j
public class SignatureVerifier {

    @Value("${razorpay.secret-key}")
    private String razorpaySecretKey;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Verifies the signature received from Razorpay after payment.
     *
     * Algorithm (matches what Razorpay does on their side):
     *   generated_signature = HMAC_SHA256(
     *     key     = razorpay_secret_key,
     *     message = razorpay_order_id + "|" + razorpay_payment_id
     *   )
     *   Then compare generated_signature with the one Razorpay sent.
     *
     * @param razorpayOrderId   The Razorpay order ID
     * @param razorpayPaymentId The Razorpay payment ID
     * @param receivedSignature The signature from Razorpay's success callback
     * @return true if signature is valid (payment is genuine)
     */
    public boolean verifyPaymentSignature(String razorpayOrderId,
                                          String razorpayPaymentId,
                                          String receivedSignature) {
        try {
            // Build the message that Razorpay signed
            // Format: "order_id|payment_id"
            String message = razorpayOrderId + "|" + razorpayPaymentId;

            // Compute our own signature using the same algorithm
            String generatedSignature = computeHmacSha256(message, razorpaySecretKey);

            // Compare — use constant-time comparison to prevent timing attacks
            // (timing attacks can guess secrets by measuring how long comparison takes)
            boolean isValid = constantTimeEquals(generatedSignature, receivedSignature);

            if (!isValid) {
                log.error("Signature mismatch! Expected={}, Got={}",
                        generatedSignature, receivedSignature);
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error verifying payment signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifies the signature on Razorpay webhook requests.
     *
     * Algorithm:
     *   generated_signature = HMAC_SHA256(
     *     key     = webhook_secret,          ← Different from API secret!
     *     message = raw_webhook_body_string  ← EXACT bytes Razorpay sent
     *   )
     *
     * IMPORTANT: Webhook secret ≠ API secret. They are configured separately
     * in the Razorpay dashboard under Settings → Webhooks.
     *
     * @param webhookBody      Raw request body string (must be exact bytes)
     * @param receivedSignature Value of X-Razorpay-Signature header
     */
    public boolean verifyWebhookSignature(String webhookBody, String receivedSignature) {
        try {
            String generatedSignature = computeHmacSha256(webhookBody, webhookSecret);
            return constantTimeEquals(generatedSignature, receivedSignature);

        } catch (Exception e) {
            log.error("Error verifying webhook signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Core cryptographic function — computes HMAC-SHA256.
     *
     * Steps:
     * 1. Create a SecretKeySpec from the key bytes
     * 2. Initialize a Mac (Message Authentication Code) instance
     * 3. Feed the message into the Mac
     * 4. Get the raw bytes output
     * 5. Convert bytes to hex string (Razorpay uses hex, not Base64)
     *
     * @param message The data to sign
     * @param secret  The secret key (only you and Razorpay know this)
     * @return Hex-encoded HMAC-SHA256 signature
     */
    private String computeHmacSha256(String message, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {

        // Create key from secret bytes
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );

        // Get Mac instance for HMAC-SHA256
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(secretKeySpec);

        // Compute hash
        byte[] hashBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

        // Convert bytes to lowercase hex string
        // Each byte → 2 hex chars (e.g., 0xFF → "ff", 0x0A → "0a")
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            // %02x = 2-character lowercase hex, padded with 0 if needed
            hexString.append(String.format("%02x", b));
        }

        return hexString.toString();
    }

    /**
     * Constant-time string comparison.
     *
     * Why not just use .equals()?
     * Java's String.equals() short-circuits on first mismatch.
     * An attacker can measure how long equals() takes to infer
     * how many characters they guessed correctly (timing attack).
     *
     * This method ALWAYS takes the same time regardless of where
     * the first mismatch occurs, making timing attacks impossible.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            // XOR: same chars → 0, different chars → non-zero
            // OR accumulates any difference
            result |= a.charAt(i) ^ b.charAt(i);
        }
        // result == 0 means all chars were identical
        return result == 0;
    }
}
