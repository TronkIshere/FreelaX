package com.marketplace.backend.dto.request.job;

import jakarta.validation.constraints.DecimalMin;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateJobRequest {

    String title;

    String description;

    @DecimalMin(value = "0.01", message = "budgetUsd phải lớn hơn 0")
    BigDecimal budgetUsd;
}
