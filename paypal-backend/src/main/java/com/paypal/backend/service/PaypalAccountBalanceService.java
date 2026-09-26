package com.paypal.backend.service;

import com.paypal.backend.dto.response.paypal.PaypalAccountBalanceResponse;
import com.paypal.backend.entity.PaypalAccountRole;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaypalAccountBalanceService {

    PaypalAccountBalanceResponse credit(UUID accountId, PaypalAccountRole role, BigDecimal amount);

    PaypalAccountBalanceResponse debit(UUID accountId, PaypalAccountRole role, BigDecimal amount);

    PaypalAccountBalanceResponse getBalance(UUID accountId, PaypalAccountRole role);
}