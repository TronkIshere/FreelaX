package com.marketplace.backend.dto.response.job;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MyApplicationJobResponse {
    UUID id;
    String title;
    String description;
    BigDecimal budgetUsd;
    String status;
    String clientDisplayName;
    LocalDateTime createdAt;
}
