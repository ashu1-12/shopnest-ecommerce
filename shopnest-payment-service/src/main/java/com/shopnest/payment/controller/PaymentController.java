package com.shopnest.payment.controller;

import com.shopnest.payment.dto.request.InitiatePaymentRequest;
import com.shopnest.payment.dto.request.RefundRequest;
import com.shopnest.payment.dto.request.VerifyPaymentRequest;
import com.shopnest.payment.dto.response.ApiResponse;
import com.shopnest.payment.dto.response.PaymentInitiateResponse;
import com.shopnest.payment.dto.response.PaymentStatusResponse;
import com.shopnest.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ┌─────────────────────────────────────────────────────────────────┐
 * │           PAYMENT CONTROLLER — REST API LAYER                   │
 * │                                                                 │
 * │  Base URL: /payments (gateway strips /api/v1 before forwarding) │
 * │                                                                 │
 * │  Endpoints:                                                     │
 * │    POST /payments/initiate     — Start payment process          │
 * │    POST /payments/verify       — Confirm payment success        │
 * │    POST /payments/webhook      — Razorpay webhook (no auth)     │
 * │    GET  /payments/{id}/status  — Get payment status             │
 * │    POST /payments/{id}/refund  — Initiate refund                │
 * │                                                                 │
 * │  Controller ONLY handles:                                       │
 * │    • HTTP request/response                                      │
 * │    • Input validation (@Valid)                                  │
 * │    • Reading headers (X-User-Id from gateway)                   │
 * │    • Delegating to PaymentService                               │
 * │                                                                 │
 * │  Controller does NOT have business logic.                       │
 * └─────────────────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/payments")       // All endpoints start with /payments
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment API", description = "Razorpay payment endpoints")  // Swagger tag
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * ── ENDPOINT 1: INITIATE PAYMENT ──────────────────────────────
     * Called when user clicks "Proceed to Pay" on checkout.
     *
     * Returns Razorpay order details so frontend can launch
     * Razorpay.js payment modal.
     *
     * Request body example:
     * {
     *   "orderId": 12345,
     *   "amount": 1299.00,
     *   "paymentMethod": "UPI",
     *   "customerName": "Rahul Sharma",
     *   "customerEmail": "rahul@example.com",
     *   "customerPhone": "9876543210"
     * }
     */
    @PostMapping("/initiate")
    @Operation(
        summary = "Initiate payment",
        description = "Creates a Razorpay order and returns payment details for frontend"
    )
    public ResponseEntity<ApiResponse<PaymentInitiateResponse>> initiatePayment(

            @Valid @RequestBody InitiatePaymentRequest request,

            // X-User-Id header is added by the API Gateway's JWT filter.
            // The gateway validates the JWT and extracts userId before forwarding here.
            // So this service never needs to touch JWTs!
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.info("Payment initiation request: orderId={}, userId={}", request.getOrderId(), userId);

        PaymentInitiateResponse response = paymentService.initiatePayment(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment order created", response));
    }

    /**
     * ── ENDPOINT 2: VERIFY PAYMENT ────────────────────────────────
     * Called by frontend AFTER user completes payment on Razorpay modal.
     *
     * Razorpay.js calls your success callback with 3 values:
     *   razorpay_order_id, razorpay_payment_id, razorpay_signature
     *
     * You send these to this endpoint for server-side verification.
     * NEVER trust payment success based on frontend callback alone!
     *
     * Request body:
     * {
     *   "razorpayOrderId": "order_OqFHToTlesTkvK",
     *   "razorpayPaymentId": "pay_OqFHTo4LwfmT9l",
     *   "razorpaySignature": "9ef4dffbfd84f1318f6739a3ce19f9d85851857ae648f114332d8401e57..."
     * }
     */
    @PostMapping("/verify")
    @Operation(
        summary = "Verify payment",
        description = "Verifies Razorpay signature and marks payment as successful"
    )
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request,
            @RequestHeader("X-User-Id") Long userId
    ) {

        log.info("Payment verification: rzpOrderId={}", request.getRazorpayOrderId());

        PaymentStatusResponse response = paymentService.verifyPayment(request);

        return ResponseEntity.ok(ApiResponse.success("Payment verified successfully", response));
    }

    /**
     * ── ENDPOINT 3: RAZORPAY WEBHOOK ──────────────────────────────
     * Razorpay calls this endpoint directly (not the user).
     * It sends events like: payment.captured, payment.failed, refund.processed
     *
     * ⚠️  IMPORTANT:
     *  - This endpoint has NO JWT authentication (Razorpay doesn't have your JWT)
     *  - Security is via webhook signature verification (done in service layer)
     *  - Must be excluded from AuthenticationFilter in gateway config
     *  - Must respond quickly (< 5 seconds) — Razorpay retries if you're slow
     *
     * This endpoint receives raw request body as String because
     * we need the exact bytes to verify the HMAC-SHA256 signature.
     * Parsing to JSON first might alter the byte sequence.
     */
    @PostMapping("/webhook")
    @Operation(
        summary = "Razorpay webhook",
        description = "Handles Razorpay payment event callbacks"
    )
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,

            // Razorpay sends this header with every webhook
            // Contains HMAC-SHA256 of the payload using your webhook secret
            @RequestHeader("X-Razorpay-Signature") String razorpaySignature
    ) {

        log.info("Webhook received, signature present: {}", razorpaySignature != null);

        paymentService.handleWebhook(payload, razorpaySignature);

        // Razorpay expects a 200 OK response to confirm receipt.
        // If we return non-200, Razorpay will retry the webhook.
        return ResponseEntity.ok("Webhook processed");
    }

    /**
     * ── ENDPOINT 4: GET PAYMENT STATUS ────────────────────────────
     * Used by:
     *  - Frontend to poll payment status
     *  - Order service to check if payment was completed
     */
    @GetMapping("/{paymentId}/status")
    @Operation(summary = "Get payment status")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(
            @PathVariable Long paymentId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId, userId);
        return ResponseEntity.ok(ApiResponse.success("Payment status retrieved", response));
    }

    /**
     * ── ENDPOINT 5: INITIATE REFUND ───────────────────────────────
     * Called when order is cancelled or item is returned.
     *
     * Request body:
     * {
     *   "refundAmount": 1299.00,  // Can be partial (e.g., 649.50 for half refund)
     *   "reason": "CUSTOMER_REQUEST"
     * }
     */
    @PostMapping("/{paymentId}/refund")
    @Operation(
        summary = "Initiate refund",
        description = "Processes full or partial refund via Razorpay"
    )
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> initiateRefund(
            @PathVariable Long paymentId,
            @Valid @RequestBody RefundRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole  // Only ADMIN or system can refund
    ) {

        log.info("Refund request: paymentId={}, amount={}, by userId={} role={}",
                paymentId, request.getRefundAmount(), userId, userRole);

        PaymentStatusResponse response = paymentService.processRefund(paymentId, request);

        return ResponseEntity.ok(ApiResponse.success("Refund initiated", response));
    }
}
