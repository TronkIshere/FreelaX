package com.payment.backend.service.impl;

import com.payment.backend.dto.response.bofa.BofaAccountBalanceResponse;
import com.payment.backend.entity.BofaAccountBalance;
import com.payment.backend.exception.ApplicationException;
import com.payment.backend.exception.ErrorCode;
import com.payment.backend.repository.BofaAccountBalanceRepository;
import com.payment.backend.service.BofaAccountBalanceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BofaAccountBalanceServiceImpl implements BofaAccountBalanceService {

    private static final BigDecimal DEFAULT_INITIAL_BALANCE_USD = new BigDecimal("10000");

    BofaAccountBalanceRepository bofaAccountBalanceRepository;

    @Override
    @Transactional
    public synchronized BofaAccountBalanceResponse credit(String bankAccountNumber, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "amount phải > 0");
        }
        BofaAccountBalance balance = getOrCreate(bankAccountNumber);
        balance.setBalance(balance.getBalance().add(amount));
        bofaAccountBalanceRepository.save(balance);
        return toResponse(balance);
    }

    @Override
    @Transactional
    public synchronized BofaAccountBalanceResponse debit(String bankAccountNumber, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "amount phải > 0");
        }
        BofaAccountBalance balance = getOrCreate(bankAccountNumber);
        if (balance.getBalance().compareTo(amount) < 0) {
            throw new ApplicationException(ErrorCode.INSUFFICIENT_BALANCE, bankAccountNumber);
        }
        balance.setBalance(balance.getBalance().subtract(amount));
        bofaAccountBalanceRepository.save(balance);
        return toResponse(balance);
    }

    @Override
    public BofaAccountBalanceResponse getBalance(String bankAccountNumber) {
        return bofaAccountBalanceRepository.findByBankAccountNumber(bankAccountNumber)
                .map(this::toResponse)
                .orElseGet(() -> BofaAccountBalanceResponse.builder()
                        .bankAccountNumber(bankAccountNumber)
                        .balance(DEFAULT_INITIAL_BALANCE_USD)
                        .build());
    }

    private BofaAccountBalance getOrCreate(String bankAccountNumber) {
        return bofaAccountBalanceRepository.findWithLockByBankAccountNumber(bankAccountNumber)
                .orElseGet(() -> {
                    BofaAccountBalance b = new BofaAccountBalance();
                    b.setBankAccountNumber(bankAccountNumber);
                    b.setBalance(DEFAULT_INITIAL_BALANCE_USD);
                    return b;
                });
    }

    private BofaAccountBalanceResponse toResponse(BofaAccountBalance b) {
        return BofaAccountBalanceResponse.builder()
                .bankAccountNumber(b.getBankAccountNumber())
                .balance(b.getBalance())
                .build();
    }
}
