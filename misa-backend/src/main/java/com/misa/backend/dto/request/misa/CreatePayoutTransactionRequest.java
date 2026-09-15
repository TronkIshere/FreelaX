package com.misa.backend.dto.request.misa;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatePayoutTransactionRequest {

    @NotBlank(message = "Mã payout không được để trống")
    String platformPayoutId;

    @NotBlank(message = "Transaction hash không được để trống")
    String transactionHash;

    String blockchain = "solana";

    String description;

    @NotNull(message = "Số USDC không được để trống")
    @DecimalMin(value = "0.000001", message = "Số USDC phải lớn hơn 0")
    BigDecimal amountUsdc;

    @NotNull(message = "Tỷ giá không được để trống")
    @DecimalMin(value = "0.000001", message = "Tỷ giá phải lớn hơn 0")
    BigDecimal exchangeRate;

    @NotNull(message = "Ngày trả thu nhập không được để trống")
    LocalDate paymentDate;
}
