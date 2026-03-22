package com.shopnest.payment.exception;

import com.shopnest.payment.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  GLOBAL EXCEPTION HANDLER                                        │
 * │                                                                  │
 * │  @RestControllerAdvice = catches exceptions from ALL controllers │
 * │  in this service and converts them to clean JSON responses.      │
 * │                                                                  │
 * │  Without this, Spring returns its default ugly error page.       │
 * │  With this, every error becomes our standard ApiResponse format. │
 * │                                                                  │
 * │  Error → HTTP Status mapping:                                    │
 * │    PaymentNotFoundException  → 404 Not Found                    │
 * │    DuplicatePaymentException → 409 Conflict                     │
 * │    PaymentException          → 400 Bad Request                  │
 * │    Validation errors         → 400 Bad Request (with details)   │
 * │    Unexpected exceptions     → 500 Internal Server Error        │
 * └──────────────────────────────────────────────────────────────────┘
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles our custom business exception for "not found" cases.
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentNotFound(
            PaymentNotFoundException ex) {

        log.warn("Payment not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles duplicate payment attempts (idempotency violation).
     * Returns 409 Conflict with the existing Razorpay order ID
     * so frontend can redirect to that payment instead.
     */
    @ExceptionHandler(DuplicatePaymentException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleDuplicatePayment(
            DuplicatePaymentException ex) {

        log.warn("Duplicate payment attempt: {}", ex.getMessage());

        Map<String, String> data = new HashMap<>();
        data.put("existingRazorpayOrderId", ex.getExistingRazorpayOrderId());
        data.put("message", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.success(ex.getMessage(), data));
    }

    /**
     * Handles general payment business exceptions.
     * e.g., Signature mismatch, refund amount exceeds payment, etc.
     */
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentException(
            PaymentException ex) {

        log.error("Payment exception: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Handles @Valid annotation failures on request DTOs.
     *
     * Example: If amount is missing, Spring throws this with:
     *   field: "amount", message: "Amount is required"
     *
     * We collect all field errors and return them together
     * so the frontend can highlight multiple invalid fields at once.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect all field-level validation errors into a map
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.success("Validation failed", errors));
    }

    /**
     * Catch-all for any unexpected exceptions.
     * Logs the full stack trace but returns a safe, generic message
     * to the client (never expose internal details in production).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error in Payment Service", ex);  // Full stack trace in logs
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again."));
    }
}
