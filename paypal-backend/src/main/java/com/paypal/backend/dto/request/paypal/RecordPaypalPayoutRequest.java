package com.paypal.backend.dto.request.paypal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecordPaypalPayoutRequest {

    @NotBlank(message = "Mã payout không được để trống")
    String platformPayoutId;

    String senderReference;

    String description;

    @NotNull(message = "Số tiền gốc (USD) không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền gốc phải lớn hơn 0")
    BigDecimal grossAmountUsd;

    @NotNull(message = "Tỷ giá tham chiếu không được để trống")
    @DecimalMin(value = "0.01", message = "Tỷ giá tham chiếu phải lớn hơn 0")
    BigDecimal midMarketRate;

    @NotNull(message = "Ngày nhận tiền không được để trống")
    LocalDateTime paymentDate;
}