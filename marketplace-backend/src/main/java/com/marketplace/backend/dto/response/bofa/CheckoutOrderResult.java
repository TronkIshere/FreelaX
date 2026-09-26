package com.marketplace.backend.dto.response.bofa;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckoutOrderResult {
    UUID id;
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