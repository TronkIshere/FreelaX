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
    com.marketplace.backend.entity.JobCategory category;
    java.util.List<String> skills;
    BigDecimal budgetUsd;
    String status;
    String clientDisplayName;
    LocalDateTime createdAt;
}
