package com.shopnest.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for initiating a refund.
 * Supports partial refunds (e.g., return one item from a multi-item order).
 */
@Data
public class RefundRequest {

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "1.00", message = "Minimum refund amount is ₹1")
    private BigDecimal refundAmount;

    @NotBlank(message = "Reason is required")
    private String reason;  // e.g., "CUSTOMER_REQUEST", "ITEM_NOT_DELIVERED", "DAMAGED"

    private String notes;   // Additional notes for internal audit trail
}
