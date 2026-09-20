package com.paypal.backend.dto.request.paypal;

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
public class CreateCheckoutOrderRequest {

    @NotNull
    UUID payeeId;

    @NotNull
    @DecimalMin(value = "0.01")
    BigDecimal amountUsd;

    @NotBlank
    String referenceId;
}
