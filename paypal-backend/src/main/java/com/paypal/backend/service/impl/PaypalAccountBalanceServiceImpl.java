package com.paypal.backend.service.impl;

import com.paypal.backend.dto.response.paypal.PaypalAccountBalanceResponse;
import com.paypal.backend.entity.PaypalAccountBalance;
import com.paypal.backend.entity.PaypalAccountRole;
import com.paypal.backend.exception.ApplicationException;
import com.paypal.backend.exception.ErrorCode;
import com.paypal.backend.repository.PaypalAccountBalanceRepository;
import com.paypal.backend.service.PaypalAccountBalanceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaypalAccountBalanceServiceImpl implements PaypalAccountBalanceService {

    PaypalAccountBalanceRepository paypalAccountBalanceRepository;

    @Override
    @Transactional
    public synchronized PaypalAccountBalanceResponse credit(UUID accountId, PaypalAccountRole role, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "amount phải > 0");
        }
        PaypalAccountBalance balance = getOrCreate(accountId, role);
        balance.setBalance(balance.getBalance().add(amount));
        paypalAccountBalanceRepository.save(balance);
        return toResponse(balance);
    }

    @Override
    @Transactional
    public synchronized PaypalAccountBalanceResponse debit(UUID accountId, PaypalAccountRole role, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "amount phải > 0");
        }
        PaypalAccountBalance balance = getOrCreate(accountId, role);
        if (balance.getBalance().compareTo(amount) < 0) {
            throw new ApplicationException(ErrorCode.INSUFFICIENT_BALANCE, accountId);
        }
        balance.setBalance(balance.getBalance().subtract(amount));
        paypalAccountBalanceRepository.save(balance);
        return toResponse(balance);
    }

    @Override
    public PaypalAccountBalanceResponse getBalance(UUID accountId, PaypalAccountRole role) {
        return paypalAccountBalanceRepository.findByAccountIdAndAccountRole(accountId, role)
                .map(this::toResponse)
                .orElseGet(() -> PaypalAccountBalanceResponse.builder()
                        .accountId(accountId)
                        .accountRole(role.name())
                        .balance(BigDecimal.ZERO)
                        .build());
    }

    private PaypalAccountBalance getOrCreate(UUID accountId, PaypalAccountRole role) {
        return paypalAccountBalanceRepository.findByAccountIdAndAccountRole(accountId, role)
                .orElseGet(() -> {
                    PaypalAccountBalance b = new PaypalAccountBalance();
                    b.setAccountId(accountId);
                    b.setAccountRole(role);
                    b.setBalance(BigDecimal.ZERO);
                    return b;
                });
    }

    private PaypalAccountBalanceResponse toResponse(PaypalAccountBalance b) {
        return PaypalAccountBalanceResponse.builder()
                .accountId(b.getAccountId())
                .accountRole(b.getAccountRole().name())
                .balance(b.getBalance())
                .build();
    }
}