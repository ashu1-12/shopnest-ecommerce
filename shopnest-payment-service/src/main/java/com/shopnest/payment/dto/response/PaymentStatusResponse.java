package com.shopnest.payment.dto.response;

import com.shopnest.payment.entity.Payment.PaymentMethod;
import com.shopnest.payment.entity.Payment.PaymentStatus;
import com.shopnest.payment.entity.Payment.RefundStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Returned by /payments/{id}/status and /payments/verify.
 * Gives full picture of a payment's current state.
 */
@Data
@Builder
public class PaymentStatusResponse {
    private Long paymentId;
    private Long orderId;
    private String razorpayOrderId;
    private String razorpayPaymentId;   // null until payment succeeds
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentMethod method;
    private LocalDateTime paidAt;       // null until payment succeeds
    private RefundStatus refundStatus;  // null if no refund
    private BigDecimal refundAmount;    // null if no refund
}
