package com.misa.backend.dto.response.misa;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PayoutTransactionResponse {
    UUID id;
    String platformPayoutId;
    UUID taxpayerId;
    String blockchain;
    String transactionHash;
    String description;
    BigDecimal amountUsdc;
    BigDecimal exchangeRate;
    BigDecimal amountVndGross;
    LocalDate paymentDate;
}
