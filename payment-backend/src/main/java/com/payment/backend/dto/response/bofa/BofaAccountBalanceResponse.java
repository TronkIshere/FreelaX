package com.payment.backend.dto.response.bofa;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BofaAccountBalanceResponse {
    UUID accountId;
    String accountRole;
    String currency;
    BigDecimal balance;
}