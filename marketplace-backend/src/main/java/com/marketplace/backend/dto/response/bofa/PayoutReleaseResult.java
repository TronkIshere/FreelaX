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
public class PayoutReleaseResult {
    UUID id;
    UUID checkoutOrderId;
    UUID payeeId;
    UUID jobId;
    BigDecimal amountUsd;
    String bofaPayoutBatchId;
    String bofaPayoutItemId;
    String status;
    LocalDateTime createdAt;
    LocalDateTime releasedAt;
}