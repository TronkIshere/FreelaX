package com.payment.backend.dto.response.bofa;

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
public class BofaCheckoutOrderResponse {
    UUID id;
    UUID payeeId;
    UUID payerUserId;
    UUID jobId;
    BigDecimal amountUsd;
    String bofaOrderId;
    String bofaCaptureId;
    String status;
    String approvalUrl;
    LocalDateTime createdAt;
    LocalDateTime capturedAt;
}
