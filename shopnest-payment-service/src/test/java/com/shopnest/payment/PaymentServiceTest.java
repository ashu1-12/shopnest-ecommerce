package com.shopnest.payment;

import com.shopnest.payment.config.RazorpayConfig;
import com.shopnest.payment.dto.request.InitiatePaymentRequest;
import com.shopnest.payment.dto.request.VerifyPaymentRequest;
import com.shopnest.payment.dto.response.PaymentInitiateResponse;
import com.shopnest.payment.dto.response.PaymentStatusResponse;
import com.shopnest.payment.entity.Payment;
import com.shopnest.payment.entity.Payment.PaymentMethod;
import com.shopnest.payment.entity.Payment.PaymentStatus;
import com.shopnest.payment.event.PaymentEventPublisher;
import com.shopnest.payment.exception.DuplicatePaymentException;
import com.shopnest.payment.exception.PaymentException;
import com.shopnest.payment.repository.PaymentRepository;
import com.shopnest.payment.service.PaymentService;
import com.shopnest.payment.util.SignatureVerifier;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Orders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  PAYMENT SERVICE — UNIT TESTS                                    │
 * │                                                                  │
 * │  @ExtendWith(MockitoExtension.class)                             │
 * │    Activates Mockito for this test class.                        │
 * │    Creates mocks for @Mock fields automatically.                 │
 * │                                                                  │
 * │  Key testing principles:                                         │
 * │    • Each test is independent — no shared state                  │
 * │    • Mock external dependencies (Razorpay, DB, Redis, Kafka)     │
 * │    • Test business logic, not Spring infrastructure              │
 * │    • Follow Given-When-Then (Arrange-Act-Assert) pattern         │
 * └──────────────────────────────────────────────────────────────────┘
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    // ── MOCKS ─────────────────────────────────────────────────────────
    // @Mock creates a fake implementation that does nothing by default.
    // We configure its behavior using when(...).thenReturn(...)

    @Mock private PaymentRepository paymentRepository;
    @Mock private RazorpayConfig razorpayConfig;
    @Mock private PaymentEventPublisher eventPublisher;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private SignatureVerifier signatureVerifier;
    @Mock private ValueOperations<String, String> valueOps;

    // Razorpay internals we need to mock
    @Mock private RazorpayClient razorpayClient;
    @Mock private Orders ordersApi;
    @Mock private Order mockRazorpayOrder;

    // ── CLASS UNDER TEST ──────────────────────────────────────────────
    // @InjectMocks creates a real PaymentService instance and injects
    // all @Mock fields into it via constructor or setter injection.
    @InjectMocks
    private PaymentService paymentService;

    private InitiatePaymentRequest validRequest;
    private static final Long USER_ID = 42L;
    private static final Long ORDER_ID = 100L;

    @BeforeEach
    void setUp() {
        // Build a valid payment initiation request for reuse in tests
        validRequest = new InitiatePaymentRequest();
        validRequest.setOrderId(ORDER_ID);
        validRequest.setAmount(new BigDecimal("1299.00"));
        validRequest.setPaymentMethod(PaymentMethod.UPI);
        validRequest.setCustomerName("Rahul Sharma");
        validRequest.setCustomerEmail("rahul@example.com");
        validRequest.setCustomerPhone("9876543210");
    }

    // ════════════════════════════════════════════════════════════════
    // TESTS: initiatePayment()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should create Razorpay order and save payment when all inputs are valid")
    void initiatePayment_ValidRequest_ReturnsPaymentDetails() throws Exception {
        // ── GIVEN (Arrange) ───────────────────────────────────────────
        // Configure mocks for the happy path

        // Redis: no existing payment for this order (not a duplicate)
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // Razorpay: return a fake order with an ID
        when(razorpayConfig.getRazorpayClient()).thenReturn(razorpayClient);
        when(razorpayClient.orders).thenReturn(ordersApi);
        when(ordersApi.create(any())).thenReturn(mockRazorpayOrder);
        when(mockRazorpayOrder.get("id")).thenReturn("order_testRazorpayId");

        // Razorpay public key
        when(razorpayConfig.getKeyId()).thenReturn("rzp_test_key");

        // DB: payment save returns the same payment with an ID
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(1L);
            return payment;
        });

        // Redis ops for storing idempotency key
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // ── WHEN (Act) ────────────────────────────────────────────────
        PaymentInitiateResponse response = paymentService.initiatePayment(validRequest, USER_ID);

        // ── THEN (Assert) ─────────────────────────────────────────────
        assertThat(response).isNotNull();
        assertThat(response.getRazorpayOrderId()).isEqualTo("order_testRazorpayId");
        assertThat(response.getRazorpayKeyId()).isEqualTo("rzp_test_key");
        assertThat(response.getAmount()).isEqualByComparingTo("1299.00");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);

        // Verify side effects
        verify(paymentRepository, times(1)).save(any(Payment.class));  // DB was called
        verify(valueOps, times(1)).set(anyString(), anyString(), anyLong(), any()); // Redis set
    }

    @Test
    @DisplayName("Should throw DuplicatePaymentException when payment already exists for order")
    void initiatePayment_DuplicateAttempt_ThrowsDuplicateException() {
        // GIVEN: Redis says this order already has an active payment
        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        Payment existingPayment = Payment.builder()
                .id(1L)
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .razorpayOrderId("order_existing123")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByOrderIdAndUserId(ORDER_ID, USER_ID))
                .thenReturn(Optional.of(existingPayment));

        // WHEN + THEN: expect exception with the existing Razorpay order ID
        assertThatThrownBy(() -> paymentService.initiatePayment(validRequest, USER_ID))
                .isInstanceOf(DuplicatePaymentException.class)
                .hasMessageContaining("Payment already initiated")
                .extracting("existingRazorpayOrderId")
                .isEqualTo("order_existing123");

        // Verify Razorpay was NOT called (no new order created)
        verifyNoInteractions(razorpayConfig);
    }

    // ════════════════════════════════════════════════════════════════
    // TESTS: verifyPayment()
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should mark payment SUCCESS when Razorpay signature is valid")
    void verifyPayment_ValidSignature_MarksSuccess() {
        // GIVEN
        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setRazorpayOrderId("order_abc123");
        request.setRazorpayPaymentId("pay_xyz789");
        request.setRazorpaySignature("valid_signature_hash");

        Payment pendingPayment = Payment.builder()
                .id(1L)
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .razorpayOrderId("order_abc123")
                .amount(new BigDecimal("1299.00"))
                .currency("INR")
                .status(PaymentStatus.PENDING)
                .method(PaymentMethod.UPI)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_abc123"))
                .thenReturn(Optional.of(pendingPayment));

        // Signature verification returns true (valid payment)
        when(signatureVerifier.verifyPaymentSignature("order_abc123", "pay_xyz789", "valid_signature_hash"))
                .thenReturn(true);

        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // WHEN
        PaymentStatusResponse response = paymentService.verifyPayment(request);

        // THEN
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getRazorpayPaymentId()).isEqualTo("pay_xyz789");

        // Kafka event must be published on success
        verify(eventPublisher, times(1)).publishPaymentSuccess(any(Payment.class));
    }

    @Test
    @DisplayName("Should throw PaymentException and NOT publish event when signature is invalid")
    void verifyPayment_InvalidSignature_ThrowsException() {
        // GIVEN: Payment exists in DB
        Payment payment = Payment.builder()
                .id(1L)
                .razorpayOrderId("order_abc123")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_abc123"))
                .thenReturn(Optional.of(payment));

        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setRazorpayOrderId("order_abc123");
        request.setRazorpayPaymentId("pay_xyz789");
        request.setRazorpaySignature("TAMPERED_SIGNATURE");

        // Signature verification fails (tampered data)
        when(signatureVerifier.verifyPaymentSignature(any(), any(), any()))
                .thenReturn(false);

        when(paymentRepository.save(any())).thenReturn(payment);

        // WHEN + THEN
        assertThatThrownBy(() -> paymentService.verifyPayment(request))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("signature verification failed");

        // Kafka event must NOT be published — payment wasn't genuinely made
        verify(eventPublisher, never()).publishPaymentSuccess(any());
    }
}
