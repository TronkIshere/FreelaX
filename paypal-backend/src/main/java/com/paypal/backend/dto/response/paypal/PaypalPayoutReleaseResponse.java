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
public class PaypalPayoutReleaseResponse {
    UUID id;
    UUID checkoutOrderId;
    UUID payeeId;
    UUID jobId;
    BigDecimal amountUsd;
    String paypalPayoutBatchId;
    String paypalPayoutItemId;
    String status;
    LocalDateTime createdAt;
    LocalDateTime releasedAt;
}
