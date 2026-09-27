package com.payment.backend.service;

import com.payment.backend.dto.response.bofa.BofaAccountBalanceResponse;

import java.math.BigDecimal;

public interface BofaAccountBalanceService {

    BofaAccountBalanceResponse credit(String bankAccountNumber, BigDecimal amount);

    BofaAccountBalanceResponse debit(String bankAccountNumber, BigDecimal amount);

    BofaAccountBalanceResponse getBalance(String bankAccountNumber);
}