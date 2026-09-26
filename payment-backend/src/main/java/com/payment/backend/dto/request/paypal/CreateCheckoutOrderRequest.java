package com.paypal.backend.dto.request.paypal;

import jakarta.validation.constraints.DecimalMin;
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

    @NotNull(message = "payerUserId không được để trống")
    UUID payerUserId;

    @NotNull(message = "jobId không được để trống")
    UUID jobId;

    @NotNull(message = "amountUsd không được để trống")
    @DecimalMin(value = "0.01", message = "amountUsd phải lớn hơn 0")
    BigDecimal amountUsd;
}