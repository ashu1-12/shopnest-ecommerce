// ════════════════════════════════════════════════════════════════════
// FILE: dto/request/InitiatePaymentRequest.java
// ════════════════════════════════════════════════════════════════════
package com.shopnest.payment.dto.request;

import com.shopnest.payment.entity.Payment.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO = Data Transfer Object
 * Used ONLY for transferring data between layers.
 * Never exposes entity internals or DB ids unnecessarily.
 *
 * @Valid on controller triggers these validation annotations.
 * Spring returns 400 Bad Request automatically if validation fails.
 */
@Data  // Lombok: @Getter + @Setter + @ToString + @EqualsAndHashCode
public class InitiatePaymentRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum payment amount is ₹1")
    @DecimalMax(value = "500000.00", message = "Maximum payment amount is ₹5,00,000")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotBlank(message = "Customer name is required")
    @Size(max = 100)
    private String customerName;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    private String customerEmail;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian phone number")
    private String customerPhone;

    // ── EMI fields (optional, only when paymentMethod = EMI) ──────
    private Integer emiMonths;  // 3, 6, 9, 12, 24
    private String bankCode;    // e.g., "HDFC", "ICICI" for EMI
}
