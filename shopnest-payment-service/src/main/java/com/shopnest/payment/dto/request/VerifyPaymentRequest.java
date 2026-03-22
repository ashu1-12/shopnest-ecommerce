package com.shopnest.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Sent from frontend after user completes Razorpay payment modal.
 * These 3 fields are provided by Razorpay.js in the success callback.
 */
@Data
public class VerifyPaymentRequest {

    @NotBlank(message = "Razorpay Order ID is required")
    private String razorpayOrderId;     // e.g., "order_OqFHToTlesTkvK"

    @NotBlank(message = "Razorpay Payment ID is required")
    private String razorpayPaymentId;   // e.g., "pay_OqFHTo4LwfmT9l"

    @NotBlank(message = "Razorpay Signature is required")
    private String razorpaySignature;   // HMAC-SHA256 signature from Razorpay
}
