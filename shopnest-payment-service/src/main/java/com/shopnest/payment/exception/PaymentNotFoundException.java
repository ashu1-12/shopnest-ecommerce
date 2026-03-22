// ═══════════════════════════════════════════════
// FILE: exception/PaymentNotFoundException.java
// ═══════════════════════════════════════════════
package com.shopnest.payment.exception;

/**
 * Thrown when a payment ID is not found in the database.
 * Maps to HTTP 404 Not Found.
 */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }
}
