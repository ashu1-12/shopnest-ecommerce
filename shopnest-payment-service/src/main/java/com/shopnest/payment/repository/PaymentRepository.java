package com.shopnest.payment.repository;

import com.shopnest.payment.entity.Payment;
import com.shopnest.payment.entity.Payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  PAYMENT REPOSITORY — DATA ACCESS LAYER                         │
 * │                                                                 │
 * │  Spring Data JPA magic:                                         │
 * │  Just by extending JpaRepository, we get FREE implementations: │
 * │    save(), findById(), findAll(), delete(), count(), etc.       │
 * │                                                                 │
 * │  Custom queries: Spring auto-generates SQL from method names!   │
 * │    findByOrderId(id) → SELECT * FROM payments WHERE order_id=?  │
 * │    findByRazorpayOrderId → SELECT * FROM payments WHERE rzp_id=?│
 * │                                                                 │
 * │  JpaRepository<Payment, Long>:                                  │
 * │    Payment = the entity type                                    │
 * │    Long    = the primary key type (matches @Id field)           │
 * └─────────────────────────────────────────────────────────────────┘
 */
@Repository  // Marks this as a Spring-managed DAO (also enables exception translation)
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // ── SPRING DATA DERIVED QUERIES ───────────────────────────────────
    // Spring reads the method name and builds the SQL automatically.
    // No @Query or SQL needed for simple lookups.

    /**
     * Find payment by Razorpay's order ID.
     * Used when receiving Razorpay webhook or verifying payment.
     * Returns Optional because the payment might not exist.
     */
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    /**
     * Find payment by Razorpay's payment ID.
     * Used in webhook handler for payment.captured event.
     */
    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    /**
     * Find payment by Razorpay's refund ID.
     * Used in webhook handler for refund.processed event.
     */
    Optional<Payment> findByRazorpayRefundId(String razorpayRefundId);

    /**
     * Find a specific order's payment — ensures userId matches
     * so users can't see other users' payments.
     * Security: always filter by userId when fetching user data!
     */
    Optional<Payment> findByOrderIdAndUserId(Long orderId, Long userId);

    /**
     * Fetch payment by ID only if it belongs to this user.
     * Prevents IDOR (Insecure Direct Object Reference) attacks.
     * IDOR = attacker changes paymentId=123 to paymentId=124
     *        to see someone else's payment details.
     */
    Optional<Payment> findByIdAndUserId(Long id, Long userId);

    /**
     * All payments for a user — for "My Orders" / payment history page.
     * OrderBy createdAt DESC = newest first.
     */
    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * All payments for a specific order.
     * An order might have multiple payment attempts (if first attempt failed).
     */
    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    /**
     * Find the latest successful payment for an order.
     * Useful when order-service wants to confirm payment before shipping.
     */
    Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

    // ── CUSTOM JPQL QUERIES ───────────────────────────────────────────
    // Use @Query when the derived query name would be too long or complex.
    // JPQL (Java Persistence Query Language) = SQL but for entity classes.

    /**
     * Find all pending payments older than X minutes.
     * Used by a scheduled job to cancel stale payment sessions.
     *
     * If a user initiates payment but abandons the page,
     * we should eventually mark it CANCELLED and release inventory.
     */
    @Query("SELECT p FROM Payment p " +
           "WHERE p.status = 'PENDING' " +
           "AND p.createdAt < :cutoffTime")
    List<Payment> findStalePendingPayments(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Total revenue for a date range — for admin dashboard/reports.
     * SUM is a JPQL aggregate function.
     */
    @Query("SELECT SUM(p.amount) FROM Payment p " +
           "WHERE p.status = 'SUCCESS' " +
           "AND p.paidAt BETWEEN :from AND :to")
    java.math.BigDecimal calculateRevenueBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * Count payments by status — for monitoring dashboard.
     */
    long countByStatus(PaymentStatus status);
}
