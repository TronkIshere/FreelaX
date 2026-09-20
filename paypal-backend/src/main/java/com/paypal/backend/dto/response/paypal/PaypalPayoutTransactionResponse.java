package com.paypal.backend.dto.response.paypal;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaypalPayoutTransactionResponse {
    UUID id;
    String platformPayoutId;
    UUID payeeId;
    String status;
    BigDecimal grossAmountUsd;
    BigDecimal midMarketRate;
    PaypalFeeBreakdown feeBreakdown;
    BigDecimal netVnd;
    LocalDateTime paymentDate;
    LocalDateTime withdrawnAt;
}
