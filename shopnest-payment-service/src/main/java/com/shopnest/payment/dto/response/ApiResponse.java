package com.shopnest.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * ┌──────────────────────────────────────────────────────────────┐
 * │  STANDARD API RESPONSE WRAPPER                               │
 * │                                                              │
 * │  Every endpoint returns this envelope so the frontend        │
 * │  always gets a consistent JSON structure:                    │
 * │                                                              │
 * │  Success:                                                    │
 * │  {                                                           │
 * │    "success": true,                                          │
 * │    "message": "Payment verified successfully",               │
 * │    "data": { ... },                                          │
 * │    "timestamp": "2024-01-15T10:30:00"                        │
 * │  }                                                           │
 * │                                                              │
 * │  Error:                                                      │
 * │  {                                                           │
 * │    "success": false,                                         │
 * │    "message": "Payment not found",                           │
 * │    "data": null,                                             │
 * │    "timestamp": "2024-01-15T10:30:00"                        │
 * │  }                                                           │
 * │                                                              │
 * │  Generic type <T> means: ApiResponse<PaymentStatusResponse>  │
 * │  or ApiResponse<List<Payment>> — flexible for any payload.   │
 * └──────────────────────────────────────────────────────────────┘
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)  // Don't include null fields in JSON output
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;                   // Generic — holds any response object
    private final LocalDateTime timestamp;

    // Private constructor — force use of factory methods below
    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Factory method for successful responses.
     * Usage: ApiResponse.success("Payment created", paymentResponse)
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /**
     * Factory method for error responses (used in GlobalExceptionHandler).
     * Usage: ApiResponse.error("Payment not found")
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
