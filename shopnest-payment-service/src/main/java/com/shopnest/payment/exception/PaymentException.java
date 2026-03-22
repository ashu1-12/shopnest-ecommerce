// ═══════════════════════════════════════════════
// FILE: exception/PaymentException.java
// ═══════════════════════════════════════════════
package com.shopnest.payment.exception;

/**
 * General payment business exception.
 * Thrown for invalid operations: signature mismatch, refund > amount, etc.
 * Maps to HTTP 400 Bad Request.
 */
public class PaymentException extends RuntimeException {
    public PaymentException(String message) {
        super(message);
    }
}
