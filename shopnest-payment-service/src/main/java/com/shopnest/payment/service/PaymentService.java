package com.shopnest.payment.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.shopnest.payment.config.RazorpayConfig;
import com.shopnest.payment.dto.request.InitiatePaymentRequest;
import com.shopnest.payment.dto.request.VerifyPaymentRequest;
import com.shopnest.payment.dto.request.RefundRequest;
import com.shopnest.payment.dto.response.PaymentInitiateResponse;
import com.shopnest.payment.dto.response.PaymentStatusResponse;
import com.shopnest.payment.entity.Payment;
import com.shopnest.payment.entity.Payment.PaymentStatus;
import com.shopnest.payment.entity.Payment.PaymentMethod;
import com.shopnest.payment.event.PaymentEventPublisher;
import com.shopnest.payment.exception.DuplicatePaymentException;
import com.shopnest.payment.exception.PaymentException;
import com.shopnest.payment.exception.PaymentNotFoundException;
import com.shopnest.payment.repository.PaymentRepository;
import com.shopnest.payment.util.SignatureVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * ┌────────────────────────────────────────────────────────────────────┐
 * │           PAYMENT SERVICE — CORE BUSINESS LOGIC                    │
 * │                                                                    │
 * │  This class contains ALL the payment business logic.               │
 * │  The controller just delegates here.                               │
 * │                                                                    │
 * │  Flow for a successful payment:                                    │
 * │  1. initiatePayment() — creates Razorpay order, saves to DB        │
 * │  2. User pays on frontend using Razorpay SDK                       │
 * │  3. verifyPayment() — validates signature, marks SUCCESS           │
 * │  4. handleWebhook() — backup verification via Razorpay webhook     │
 * │  5. publishPaymentSuccess() — Kafka event → order/notification     │
 * └────────────────────────────────────────────────────────────────────┘
 */
@Service            // Spring Bean — business logic layer
@Slf4j              // Lombok logger
@RequiredArgsConstructor  // Lombok: constructor injection for all @final fields
@Transactional      // All public methods run in a DB transaction by default
public class PaymentService {

    // ── DEPENDENCIES (injected via constructor by @RequiredArgsConstructor) ─
    private final PaymentRepository paymentRepository;
    private final RazorpayConfig razorpayConfig;
    private final PaymentEventPublisher eventPublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final SignatureVerifier signatureVerifier;

    // ── IDEMPOTENCY SETTINGS ─────────────────────────────────────────────
    private static final String IDEMPOTENCY_KEY_PREFIX = "payment:idempotency:";
    private static final long IDEMPOTENCY_TTL_MINUTES = 30;  // Keys expire after 30 min

    /**
     * ════════════════════════════════════════════════════════════════
     * STEP 1: INITIATE PAYMENT
     * ════════════════════════════════════════════════════════════════
     *
     * Called when user clicks "Proceed to Pay" on checkout page.
     *
     * What happens:
     *  a) Check for duplicate payment attempt (idempotency)
     *  b) Create an Order in Razorpay (this gives us an order ID)
     *  c) Save payment record in our DB with status PENDING
     *  d) Return Razorpay order details to frontend
     *
     * Frontend then uses these details with Razorpay.js to show
     * the payment modal (UPI, card, etc.)
     */
    public PaymentInitiateResponse initiatePayment(InitiatePaymentRequest request,
                                                    Long userId) {

        log.info("Initiating payment for orderId={}, userId={}, amount={}",
                request.getOrderId(), userId, request.getAmount());

        // ── Idempotency Check ────────────────────────────────────────────
        // If user clicks "Pay" twice quickly, we should NOT create two payments.
        // We use Redis to store a lock key for 30 minutes.
        String idempotencyKey = buildIdempotencyKey(request.getOrderId(), userId);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(IDEMPOTENCY_KEY_PREFIX + idempotencyKey))) {
            // This order was already initiated — return existing payment
            Payment existingPayment = paymentRepository
                    .findByOrderIdAndUserId(request.getOrderId(), userId)
                    .orElseThrow(() -> new PaymentException("Idempotency inconsistency"));

            log.warn("Duplicate payment attempt detected for orderId={}", request.getOrderId());
            throw new DuplicatePaymentException(
                    "Payment already initiated for this order",
                    existingPayment.getRazorpayOrderId()
            );
        }

        try {
            // ── Create Order in Razorpay ──────────────────────────────────
            // Razorpay works in PAISE (1 rupee = 100 paise)
            // So ₹1299.00 → 129900 paise
            long amountInPaise = request.getAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .longValue();

            // Build the Razorpay order request
            JSONObject razorpayOrderRequest = new JSONObject();
            razorpayOrderRequest.put("amount", amountInPaise);
            razorpayOrderRequest.put("currency", "INR");
            razorpayOrderRequest.put("receipt", "order_" + request.getOrderId());

            // Add notes — visible on Razorpay dashboard (helpful for debugging)
            JSONObject notes = new JSONObject();
            notes.put("shopnest_order_id", request.getOrderId());
            notes.put("user_id", userId);
            notes.put("customer_email", request.getCustomerEmail());
            razorpayOrderRequest.put("notes", notes);

            // ★ Make API call to Razorpay ★
            // This creates the payment order on Razorpay's side
            RazorpayClient razorpayClient = razorpayConfig.getRazorpayClient();
            Order razorpayOrder = razorpayClient.orders.create(razorpayOrderRequest);

            String rzpOrderId = razorpayOrder.get("id");  // e.g., "order_OqFHToTlesTkvK"
            log.info("Razorpay order created: rzpOrderId={}", rzpOrderId);

            // ── Save Payment to Our Database ─────────────────────────────
            Payment payment = Payment.builder()
                    .orderId(request.getOrderId())
                    .userId(userId)
                    .amount(request.getAmount())
                    .currency("INR")
                    .razorpayOrderId(rzpOrderId)
                    .status(PaymentStatus.PENDING)
                    .method(request.getPaymentMethod())
                    .idempotencyKey(idempotencyKey)
                    .emiMonths(request.getEmiMonths())
                    .build();

            payment = paymentRepository.save(payment);

            // ── Set Idempotency Key in Redis ─────────────────────────────
            // Prevents duplicate payment creation for 30 minutes
            redisTemplate.opsForValue().set(
                    IDEMPOTENCY_KEY_PREFIX + idempotencyKey,
                    rzpOrderId,
                    IDEMPOTENCY_TTL_MINUTES,
                    TimeUnit.MINUTES
            );

            // ── Return Response to Frontend ──────────────────────────────
            // Frontend needs: rzpOrderId, key_id, amount to initialize Razorpay.js
            return PaymentInitiateResponse.builder()
                    .paymentId(payment.getId())
                    .razorpayOrderId(rzpOrderId)
                    .razorpayKeyId(razorpayConfig.getKeyId()) // Public key (safe to expose)
                    .amount(request.getAmount())
                    .currency("INR")
                    .customerName(request.getCustomerName())
                    .customerEmail(request.getCustomerEmail())
                    .customerPhone(request.getCustomerPhone())
                    .status(PaymentStatus.PENDING)
                    .build();

        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed for orderId={}: {}",
                    request.getOrderId(), e.getMessage());
            throw new PaymentException("Failed to create payment order: " + e.getMessage());
        }
    }

    /**
     * ════════════════════════════════════════════════════════════════
     * STEP 2: VERIFY PAYMENT
     * ════════════════════════════════════════════════════════════════
     *
     * Called after user completes payment on Razorpay modal.
     * Razorpay calls your frontend's success callback with:
     *   - razorpay_order_id
     *   - razorpay_payment_id
     *   - razorpay_signature
     *
     * We MUST verify the signature before marking payment as successful.
     * This prevents fraud — someone claiming to have paid without actually paying.
     *
     * Signature algorithm:
     *   HMAC-SHA256(razorpay_order_id + "|" + razorpay_payment_id, secret_key)
     */
    @Transactional
    public PaymentStatusResponse verifyPayment(VerifyPaymentRequest request) {

        log.info("Verifying payment: rzpOrderId={}, rzpPaymentId={}",
                request.getRazorpayOrderId(), request.getRazorpayPaymentId());

        // ── Find the payment in our DB ────────────────────────────────────
        Payment payment = paymentRepository
                .findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found for Razorpay order: " + request.getRazorpayOrderId()));

        // ── Check payment isn't already processed ────────────────────────
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.warn("Payment already verified: rzpOrderId={}", request.getRazorpayOrderId());
            return buildStatusResponse(payment);
        }

        // ── ★ SIGNATURE VERIFICATION ★ ────────────────────────────────────
        // This is CRITICAL — it proves Razorpay actually processed this payment.
        // Without this check, anyone could fake a payment success.
        boolean isSignatureValid = signatureVerifier.verifyPaymentSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );

        if (!isSignatureValid) {
            log.error("SIGNATURE MISMATCH — possible fraud attempt! rzpOrderId={}",
                    request.getRazorpayOrderId());

            // Mark as failed with security reason
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Signature verification failed");
            payment.setFailureCode("SIGNATURE_MISMATCH");
            paymentRepository.save(payment);

            throw new PaymentException("Payment signature verification failed. Possible fraud.");
        }

        // ── Update Payment Record ─────────────────────────────────────────
        payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
        payment.setRazorpaySignature(request.getRazorpaySignature());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        log.info("Payment verified successfully: paymentId={}, rzpPaymentId={}",
                payment.getId(), request.getRazorpayPaymentId());

        // ── Publish Success Event to Kafka ────────────────────────────────
        // Order service listens: updates order status to PAYMENT_SUCCESS
        // Notification service listens: sends payment confirmation email/SMS
        eventPublisher.publishPaymentSuccess(payment);

        return buildStatusResponse(payment);
    }

    /**
     * ════════════════════════════════════════════════════════════════
     * STEP 3: PROCESS REFUND
     * ════════════════════════════════════════════════════════════════
     *
     * Called when:
     *  - User cancels order
     *  - Item returned
     *  - Order not delivered
     *
     * Razorpay supports:
     *  - Full refund (100% of amount)
     *  - Partial refund (e.g., return only one item from a bundle)
     */
    @Transactional
    public PaymentStatusResponse processRefund(Long paymentId, RefundRequest request) {

        log.info("Processing refund for paymentId={}, amount={}", paymentId, request.getRefundAmount());

        // ── Fetch and validate ────────────────────────────────────────────
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentException("Can only refund successful payments");
        }

        // Validate refund amount isn't more than original payment
        if (request.getRefundAmount().compareTo(payment.getAmount()) > 0) {
            throw new PaymentException("Refund amount cannot exceed payment amount");
        }

        try {
            // ── Call Razorpay Refund API ──────────────────────────────────
            long refundAmountInPaise = request.getRefundAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .longValue();

            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount", refundAmountInPaise);
            refundRequest.put("notes", new JSONObject()
                    .put("reason", request.getReason())
                    .put("shopnest_payment_id", paymentId));

            // ★ Razorpay refund API call ★
            RazorpayClient client = razorpayConfig.getRazorpayClient();
            com.razorpay.Refund razorpayRefund = client.payments
                    .refund(payment.getRazorpayPaymentId(), refundRequest);

            String refundId = razorpayRefund.get("id");  // e.g., "rfnd_OqFHToTlesTkvK"
            log.info("Razorpay refund created: refundId={}", refundId);

            // ── Update Payment Record ─────────────────────────────────────
            payment.setRazorpayRefundId(refundId);
            payment.setRefundAmount(request.getRefundAmount());
            payment.setRefundStatus(Payment.RefundStatus.INITIATED);
            payment.setStatus(PaymentStatus.REFUND_INITIATED);
            payment = paymentRepository.save(payment);

            // ── Publish Refund Event ──────────────────────────────────────
            eventPublisher.publishRefundInitiated(payment);

            return buildStatusResponse(payment);

        } catch (RazorpayException e) {
            log.error("Razorpay refund failed: {}", e.getMessage());
            payment.setRefundStatus(Payment.RefundStatus.FAILED);
            paymentRepository.save(payment);
            throw new PaymentException("Refund failed: " + e.getMessage());
        }
    }

    /**
     * ════════════════════════════════════════════════════════════════
     * WEBHOOK HANDLER
     * ════════════════════════════════════════════════════════════════
     *
     * Razorpay sends POST requests to our /payments/webhook endpoint
     * for various events:
     *   - payment.authorized
     *   - payment.captured
     *   - payment.failed
     *   - refund.processed
     *   - order.paid
     *
     * Why do we need webhooks if we already have verifyPayment()?
     *  → Backup confirmation. If user closes browser after paying,
     *    webhook still confirms the payment.
     *  → Async processing for COD confirmation etc.
     *
     * Security: Razorpay signs the webhook body with your webhook secret.
     * We MUST verify this signature before trusting the webhook.
     */
    @Transactional
    public void handleWebhook(String payload, String razorpaySignature) {

        log.info("Received Razorpay webhook");

        // ── Verify webhook signature ──────────────────────────────────────
        // This proves the request genuinely came from Razorpay, not an attacker.
        boolean isValid = signatureVerifier.verifyWebhookSignature(payload, razorpaySignature);

        if (!isValid) {
            log.error("INVALID WEBHOOK SIGNATURE — rejecting webhook");
            throw new PaymentException("Invalid webhook signature");
        }

        // ── Parse webhook payload ─────────────────────────────────────────
        JSONObject webhookData = new JSONObject(payload);
        String event = webhookData.getString("event");
        log.info("Processing webhook event: {}", event);

        switch (event) {
            case "payment.captured" -> handlePaymentCaptured(webhookData);
            case "payment.failed" -> handlePaymentFailed(webhookData);
            case "refund.processed" -> handleRefundProcessed(webhookData);
            case "order.paid" -> handleOrderPaid(webhookData);
            default -> log.info("Unhandled webhook event: {}", event);
        }
    }

    // ── PRIVATE WEBHOOK HANDLERS ─────────────────────────────────────────

    private void handlePaymentCaptured(JSONObject webhookData) {
        String rzpPaymentId = webhookData
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity")
                .getString("id");

        paymentRepository.findByRazorpayPaymentId(rzpPaymentId).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.SUCCESS) {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(LocalDateTime.now());
                paymentRepository.save(payment);
                eventPublisher.publishPaymentSuccess(payment);
                log.info("Payment captured via webhook: {}", rzpPaymentId);
            }
        });
    }

    private void handlePaymentFailed(JSONObject webhookData) {
        JSONObject paymentEntity = webhookData
                .getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String rzpOrderId = paymentEntity.getString("order_id");
        String errorDesc = paymentEntity.optString("error_description", "Unknown error");

        paymentRepository.findByRazorpayOrderId(rzpOrderId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(errorDesc);
            paymentRepository.save(payment);
            eventPublisher.publishPaymentFailed(payment);
            log.info("Payment failed via webhook: rzpOrderId={}", rzpOrderId);
        });
    }

    private void handleRefundProcessed(JSONObject webhookData) {
        String refundId = webhookData
                .getJSONObject("payload")
                .getJSONObject("refund")
                .getJSONObject("entity")
                .getString("id");

        paymentRepository.findByRazorpayRefundId(refundId).ifPresent(payment -> {
            payment.setRefundStatus(Payment.RefundStatus.PROCESSED);
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            eventPublisher.publishRefundProcessed(payment);
            log.info("Refund processed via webhook: refundId={}", refundId);
        });
    }

    private void handleOrderPaid(JSONObject webhookData) {
        // When the entire order amount is paid (useful for partial payments)
        log.info("Order paid event received");
    }

    // ── UTILITY METHODS ──────────────────────────────────────────────────

    @Transactional(readOnly = true)  // Read-only = no DB write lock (faster)
    public PaymentStatusResponse getPaymentStatus(Long paymentId, Long userId) {
        Payment payment = paymentRepository
                .findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found: " + paymentId));
        return buildStatusResponse(payment);
    }

    private String buildIdempotencyKey(Long orderId, Long userId) {
        // Simple but effective: orderId:userId combination uniquely identifies a payment attempt
        return orderId + ":" + userId;
    }

    private PaymentStatusResponse buildStatusResponse(Payment payment) {
        return PaymentStatusResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .razorpayOrderId(payment.getRazorpayOrderId())
                .razorpayPaymentId(payment.getRazorpayPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .method(payment.getMethod())
                .paidAt(payment.getPaidAt())
                .refundStatus(payment.getRefundStatus())
                .refundAmount(payment.getRefundAmount())
                .build();
    }
}
