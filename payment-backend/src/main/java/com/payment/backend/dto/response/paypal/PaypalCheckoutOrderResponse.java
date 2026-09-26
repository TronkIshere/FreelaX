package com.paypal.backend.dto.response.paypal;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaypalCheckoutOrderResponse {
    UUID id;
    UUID payeeId;
    UUID payerUserId;
    UUID jobId;
    BigDecimal amountUsd;
    String paypalOrderId;
    String paypalCaptureId;
    String status;
    String approvalUrl;
    LocalDateTime createdAt;
    LocalDateTime capturedAt;
}
