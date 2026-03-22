// ═══════════════════════════════════════════════════════════
// FILE: dto/response/PaymentInitiateResponse.java
// ═══════════════════════════════════════════════════════════
package com.shopnest.payment.dto.response;

import com.shopnest.payment.entity.Payment.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Returned to the frontend after /payments/initiate.
 *
 * Frontend uses these fields to initialize the Razorpay.js modal:
 *
 *   var options = {
 *     key: response.razorpayKeyId,          // Your Razorpay Key ID
 *     amount: response.amount * 100,        // In paise
 *     currency: response.currency,
 *     order_id: response.razorpayOrderId,   // ← CRITICAL: links to Razorpay order
 *     name: "ShopNest",
 *     prefill: {
 *       name:  response.customerName,
 *       email: response.customerEmail,
 *       contact: response.customerPhone
 *     },
 *     handler: function(paymentResult) {
 *       // On success, send paymentResult to /payments/verify
 *     }
 *   };
 *   var rzp = new Razorpay(options);
 *   rzp.open();
 */
@Data
@Builder
public class PaymentInitiateResponse {
    private Long paymentId;             // Our internal payment ID
    private String razorpayOrderId;     // Razorpay's order ID (for the modal)
    private String razorpayKeyId;       // Razorpay public key (safe to expose to frontend)
    private BigDecimal amount;          // In rupees
    private String currency;            // "INR"
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private PaymentStatus status;       // Always PENDING at this stage
}
