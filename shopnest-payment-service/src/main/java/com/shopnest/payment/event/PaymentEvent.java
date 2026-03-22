package com.shopnest.payment.event;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The payload published to Kafka topics.
 *
 * Keep this lean — only what consumers need to react.
 * Don't send the full Payment entity (DB internals should stay internal).
 *
 * Consumers (order-service, notification-service) deserialize this
 * from the Kafka message JSON.
 */
@Data
@Builder
public class PaymentEvent {

    private String eventType;           // "PAYMENT_SUCCESS", "PAYMENT_FAILED", etc.
    private Long paymentId;             // Our internal payment ID
    private Long orderId;               // The order this payment is for
    private Long userId;                // The user who paid
    private BigDecimal amount;
    private String currency;
    private String razorpayPaymentId;   // null if payment failed
    private String razorpayRefundId;    // null if not a refund event
    private BigDecimal refundAmount;    // null if not a refund event
    private LocalDateTime occurredAt;   // When this event happened
}
