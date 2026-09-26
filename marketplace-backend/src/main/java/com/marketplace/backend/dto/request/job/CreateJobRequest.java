package com.marketplace.backend.dto.request.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateJobRequest {

    UUID freelancerId;

    @NotBlank(message = "Tiêu đề không được để trống")
    String title;

    String description;

    @NotNull(message = "Ngân sách không được để trống")
    @Positive(message = "Ngân sách phải lớn hơn 0")
    BigDecimal budgetUsd;
}