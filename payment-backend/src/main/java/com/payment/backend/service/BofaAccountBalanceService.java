package com.payment.backend.service;

import com.payment.backend.dto.response.bofa.BofaAccountBalanceResponse;
import com.payment.backend.entity.BofaAccountRole;

import java.math.BigDecimal;
import java.util.UUID;

public interface BofaAccountBalanceService {

    BofaAccountBalanceResponse credit(UUID accountId, BofaAccountRole role, BigDecimal amount);

    BofaAccountBalanceResponse debit(UUID accountId, BofaAccountRole role, BigDecimal amount);

    BofaAccountBalanceResponse getBalance(UUID accountId, BofaAccountRole role);
}