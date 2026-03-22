package com.shopnest.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ┌────────────────────────────────────────────────────────────────┐
 * │           PAYMENT ENTITY — DATABASE TABLE                      │
 * │                                                                │
 * │  This class maps 1:1 to the 'payments' table in MySQL.         │
 * │  Every payment attempt creates a row here.                     │
 * │                                                                │
 * │  Why BigDecimal for amount?                                     │
 * │  → Never use float/double for money — rounding errors!         │
 * │    0.1 + 0.2 in float = 0.30000000000000004                    │
 * │  BigDecimal gives exact decimal arithmetic.                     │
 * │                                                                │
 * │  Why store Razorpay IDs?                                       │
 * │  → Razorpay's order ID lets us look up the payment on their    │
 * │    dashboard. Payment ID is proof of successful payment.       │
 * └────────────────────────────────────────────────────────────────┘
 */
@Entity
@Table(
    name = "payments",
    indexes = {
        // These indexes make queries fast on commonly searched columns
        @Index(name = "idx_order_id", columnList = "orderId"),
        @Index(name = "idx_razorpay_order_id", columnList = "razorpayOrderId"),
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_status", columnList = "status")
    }
)
@EntityListeners(AuditingEntityListener.class) // Enables @CreatedDate and @LastModifiedDate
@Getter             // Lombok: generates all getters
@Setter             // Lombok: generates all setters
@Builder            // Lombok: enables Payment.builder().amount(...).build()
@NoArgsConstructor  // Lombok: required by JPA (JPA needs no-arg constructor)
@AllArgsConstructor // Lombok: needed by @Builder
public class Payment {

    // ── PRIMARY KEY ──────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // Auto-increment in MySQL
    private Long id;

    // ── BUSINESS IDENTIFIERS ─────────────────────────────────────

    @Column(nullable = false)
    private Long orderId;        // Links to Order in order-service

    @Column(nullable = false)
    private Long userId;         // Who is paying (from JWT via X-User-Id header)

    // ── RAZORPAY IDENTIFIERS ─────────────────────────────────────

    @Column(unique = true)
    private String razorpayOrderId;   // e.g., "order_OqFHToTlesTkvK"
                                       // Created when user initiates payment

    @Column(unique = true)
    private String razorpayPaymentId; // e.g., "pay_OqFHToTlesTkvK"
                                       // Set ONLY after successful payment

    @Column
    private String razorpaySignature; // HMAC-SHA256 signature for verification
                                       // We verify this to confirm payment is genuine

    // ── PAYMENT DETAILS ──────────────────────────────────────────

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;         // In rupees (e.g., 1299.00)
                                        // Razorpay works in paise (multiply by 100 when calling API)

    @Column(nullable = false, length = 3)
    private String currency;           // "INR" (default), "USD" etc.

    @Enumerated(EnumType.STRING)       // Store as "PENDING" not 0 in DB (more readable)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;      // UPI, CARD, NET_BANKING, WALLET, COD, EMI

    // ── EMI DETAILS (null if not EMI payment) ────────────────────
    @Column
    private Integer emiMonths;         // 3, 6, 9, 12, 24

    @Column(precision = 10, scale = 2)
    private BigDecimal emiAmount;      // Monthly EMI amount

    // ── FAILURE DETAILS ──────────────────────────────────────────
    @Column
    private String failureReason;      // Razorpay error message if payment failed

    @Column
    private String failureCode;        // Razorpay error code (e.g., "BAD_REQUEST_ERROR")

    // ── REFUND DETAILS ───────────────────────────────────────────
    @Column
    private String razorpayRefundId;   // Set when refund is processed

    @Enumerated(EnumType.STRING)
    @Column
    private RefundStatus refundStatus; // null, INITIATED, PROCESSED, FAILED

    @Column(precision = 12, scale = 2)
    private BigDecimal refundAmount;   // Can be partial refund

    // ── IDEMPOTENCY ───────────────────────────────────────────────
    @Column(unique = true)
    private String idempotencyKey;     // Prevents duplicate payments
                                        // = "orderId:userId:timestamp" hash

    // ── AUDIT FIELDS ─────────────────────────────────────────────
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime paidAt;      // Exact time payment was confirmed

    // ── PAYMENT STATUS ENUM ───────────────────────────────────────
    public enum PaymentStatus {
        PENDING,      // Order created in Razorpay, user hasn't paid yet
        PROCESSING,   // User on payment page
        SUCCESS,      // Payment confirmed via webhook
        FAILED,       // Payment failed (insufficient funds, etc.)
        CANCELLED,    // User closed payment window
        REFUND_INITIATED,
        REFUNDED      // Full/partial refund processed
    }

    // ── PAYMENT METHOD ENUM ───────────────────────────────────────
    public enum PaymentMethod {
        UPI,           // Google Pay, PhonePe, Paytm UPI
        CARD,          // Credit/Debit card
        NET_BANKING,   // Internet banking
        WALLET,        // Paytm wallet, Mobikwik, FreeCharge
        EMI,           // Equated Monthly Instalments
        COD,           // Cash on Delivery
        BNPL           // Buy Now Pay Later (Simpl, LazyPay)
    }

    // ── REFUND STATUS ENUM ────────────────────────────────────────
    public enum RefundStatus {
        INITIATED,   // Refund request sent to Razorpay
        PROCESSED,   // Razorpay confirmed refund
        FAILED       // Refund attempt failed
    }
}
