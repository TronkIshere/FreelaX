package com.payment.backend.dto.response.bofa;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BofaAccountBalanceResponse {
    String bankAccountNumber;
    BigDecimal balance;
}