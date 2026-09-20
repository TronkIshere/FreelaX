package com.paypal.backend.marketplace.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class JobResponse {

    UUID id;
    UUID clientUserId;
    String title;
    String description;
    BigDecimal budgetUsd;
    UUID checkoutOrderId;
    String status;
    LocalDateTime createdAt;
}
