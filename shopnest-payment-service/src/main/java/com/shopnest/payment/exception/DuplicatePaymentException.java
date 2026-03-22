package com.shopnest.payment.exception;

import lombok.Getter;

/**
 * Thrown when a payment is initiated for an order that already has an active payment.
 * Carries the existing Razorpay order ID so the frontend can redirect
 * to the already-created payment instead of creating a new one.
 * Maps to HTTP 409 Conflict.
 */
@Getter
public class DuplicatePaymentException extends RuntimeException {

    private final String existingRazorpayOrderId;

    public DuplicatePaymentException(String message, String existingRazorpayOrderId) {
        super(message);
        this.existingRazorpayOrderId = existingRazorpayOrderId;
    }
}
