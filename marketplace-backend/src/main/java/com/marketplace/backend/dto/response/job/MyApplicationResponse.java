package com.marketplace.backend.dto.response.job;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MyApplicationResponse {
    UUID id;
    String status;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    MyApplicationJobResponse job;
}
