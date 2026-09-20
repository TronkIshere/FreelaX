package com.paypal.backend.marketplace.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateJobRequest {

    @NotBlank
    String title;

    @NotBlank
    String description;

    @NotNull
    @DecimalMin(value = "0.01")
    BigDecimal budgetUsd;
}
