package com.marketplace.backend.dto.request.job;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "freelancerUserId không được để trống")
    UUID freelancerUserId;

    @NotBlank(message = "title không được để trống")
    String title;

    String description;

    @NotNull(message = "budgetUsd không được để trống")
    @DecimalMin(value = "0.01", message = "budgetUsd phải lớn hơn 0")
    BigDecimal budgetUsd;
}
