package com.shopnest.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopnest.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  PAYMENT EVENT PUBLISHER — KAFKA PRODUCER                        │
 * │                                                                  │
 * │  After a payment succeeds/fails, we publish events to Kafka.     │
 * │  Other services subscribe and react:                             │
 * │                                                                  │
 * │  TOPIC: payment.success                                          │
 * │    → Order Service:        Update order status to PAID           │
 * │    → Inventory Service:    Confirm stock deduction               │
 * │    → Notification Service: Send "Payment received" email/SMS     │
 * │                                                                  │
 * │  TOPIC: payment.failed                                           │
 * │    → Order Service:        Keep order in PENDING_PAYMENT         │
 * │    → Inventory Service:    Release reserved stock                │
 * │    → Notification Service: Send "Payment failed" email           │
 * │                                                                  │
 * │  TOPIC: payment.refund.initiated                                 │
 * │    → Notification Service: Send "Refund initiated" email         │
 * │                                                                  │
 * │  TOPIC: payment.refund.processed                                 │
 * │    → Notification Service: Send "Refund processed" email         │
 * │    → Order Service:        Mark order as REFUNDED                │
 * │                                                                  │
 * │  Why Kafka instead of direct REST calls?                         │
 * │  → Decoupled: Payment service doesn't know about Order service   │
 * │  → Resilient: If Order service is down, events queue up in Kafka │
 * │  → Scalable: Multiple consumers can process the same event       │
 * └──────────────────────────────────────────────────────────────────┘
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    // KafkaTemplate sends messages to Kafka topics
    // <String, String> = key type, value type (we serialize to JSON string)
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;  // Jackson JSON serializer

    // ── KAFKA TOPIC NAMES ─────────────────────────────────────────────
    // Define as constants to avoid typos scattered across codebase
    public static final String TOPIC_PAYMENT_SUCCESS   = "payment.success";
    public static final String TOPIC_PAYMENT_FAILED    = "payment.failed";
    public static final String TOPIC_REFUND_INITIATED  = "payment.refund.initiated";
    public static final String TOPIC_REFUND_PROCESSED  = "payment.refund.processed";

    /**
     * Publishes payment.success event.
     * Triggered after successful Razorpay signature verification.
     */
    public void publishPaymentSuccess(Payment payment) {
        PaymentEvent event = buildEvent("PAYMENT_SUCCESS", payment);
        sendEvent(TOPIC_PAYMENT_SUCCESS, payment.getOrderId().toString(), event);
        log.info("Published PAYMENT_SUCCESS event for orderId={}", payment.getOrderId());
    }

    /**
     * Publishes payment.failed event.
     * Triggered when Razorpay reports payment failure via webhook.
     */
    public void publishPaymentFailed(Payment payment) {
        PaymentEvent event = buildEvent("PAYMENT_FAILED", payment);
        sendEvent(TOPIC_PAYMENT_FAILED, payment.getOrderId().toString(), event);
        log.info("Published PAYMENT_FAILED event for orderId={}", payment.getOrderId());
    }

    /**
     * Publishes refund.initiated event.
     * Triggered after we call Razorpay refund API successfully.
     */
    public void publishRefundInitiated(Payment payment) {
        PaymentEvent event = buildEvent("REFUND_INITIATED", payment);
        sendEvent(TOPIC_REFUND_INITIATED, payment.getOrderId().toString(), event);
        log.info("Published REFUND_INITIATED event for orderId={}", payment.getOrderId());
    }

    /**
     * Publishes refund.processed event.
     * Triggered when Razorpay webhook confirms refund completion.
     */
    public void publishRefundProcessed(Payment payment) {
        PaymentEvent event = buildEvent("REFUND_PROCESSED", payment);
        sendEvent(TOPIC_REFUND_PROCESSED, payment.getOrderId().toString(), event);
        log.info("Published REFUND_PROCESSED event for orderId={}", payment.getOrderId());
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────

    /**
     * Builds the event payload from a Payment entity.
     * Consumers receive exactly what they need — no full entity.
     */
    private PaymentEvent buildEvent(String eventType, Payment payment) {
        return PaymentEvent.builder()
                .eventType(eventType)
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .razorpayPaymentId(payment.getRazorpayPaymentId())
                .razorpayRefundId(payment.getRazorpayRefundId())
                .refundAmount(payment.getRefundAmount())
                .occurredAt(LocalDateTime.now())
                .build();
    }

    /**
     * Sends the event to a Kafka topic asynchronously.
     *
     * Kafka message structure:
     *   Topic:     "payment.success"
     *   Key:       "12345" (orderId as string)
     *   Value:     JSON string of PaymentEvent
     *
     * Why use orderId as the Kafka key?
     * Kafka partitions messages by key. Same key → same partition.
     * This guarantees ORDER-level event ordering:
     * All events for order #12345 go to the same partition
     * and are consumed in sequence. Important for:
     *   PENDING → PAYMENT_INITIATED → SUCCESS → SHIPPED
     * (You don't want SUCCESS processed before PAYMENT_INITIATED)
     */
    private void sendEvent(String topic, String key, PaymentEvent event) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);

            // sendAsync = non-blocking (doesn't wait for Kafka broker confirmation)
            CompletableFuture<SendResult<String, String>> future =
                    kafkaTemplate.send(topic, key, jsonPayload);

            // Attach callbacks for logging (doesn't block the calling thread)
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event to topic={}, key={}: {}",
                            topic, key, ex.getMessage());
                    // In production: add retry logic or dead-letter queue
                } else {
                    log.debug("Event published to topic={}, partition={}, offset={}",
                            topic,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });

        } catch (Exception e) {
            log.error("Error serializing payment event: {}", e.getMessage());
            // Don't throw — payment is already saved to DB.
            // Event failure shouldn't fail the payment response.
            // A scheduled job can replay failed events from DB.
        }
    }
}
