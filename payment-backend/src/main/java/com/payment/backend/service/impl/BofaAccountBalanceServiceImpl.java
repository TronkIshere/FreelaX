package com.payment.backend.service.impl;

import com.payment.backend.dto.response.bofa.BofaAccountBalanceResponse;
import com.payment.backend.entity.BofaAccountBalance;
import com.payment.backend.entity.BofaAccountRole;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BofaAccountBalanceServiceImpl implements BofaAccountBalanceService {

    BofaAccountBalanceRepository bofaAccountBalanceRepository;

    @Override
    @Transactional
    public synchronized BofaAccountBalanceResponse credit(UUID accountId, BofaAccountRole role, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "amount phải > 0");
        }
        BofaAccountBalance balance = getOrCreate(accountId, role);
        balance.setBalance(balance.getBalance().add(amount));
        bofaAccountBalanceRepository.save(balance);
        return toResponse(balance);
    }

    @Override
    @Transactional
    public synchronized BofaAccountBalanceResponse debit(UUID accountId, BofaAccountRole role, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "amount phải > 0");
        }
        BofaAccountBalance balance = getOrCreate(accountId, role);
        if (balance.getBalance().compareTo(amount) < 0) {
            throw new ApplicationException(ErrorCode.INSUFFICIENT_BALANCE, accountId);
        }
        balance.setBalance(balance.getBalance().subtract(amount));
        bofaAccountBalanceRepository.save(balance);
        return toResponse(balance);
    }

    @Override
    public BofaAccountBalanceResponse getBalance(UUID accountId, BofaAccountRole role) {
        return bofaAccountBalanceRepository.findByAccountIdAndAccountRole(accountId, role)
                .map(this::toResponse)
                .orElseGet(() -> BofaAccountBalanceResponse.builder()
                        .accountId(accountId)
                        .accountRole(role.name())
                        .balance(BigDecimal.ZERO)
                        .build());
    }

    private BofaAccountBalance getOrCreate(UUID accountId, BofaAccountRole role) {
        return bofaAccountBalanceRepository.findByAccountIdAndAccountRole(accountId, role)
                .orElseGet(() -> {
                    BofaAccountBalance b = new BofaAccountBalance();
                    b.setAccountId(accountId);
                    b.setAccountRole(role);
                    b.setBalance(BigDecimal.ZERO);
                    return b;
                });
    }

    private BofaAccountBalanceResponse toResponse(BofaAccountBalance b) {
        return BofaAccountBalanceResponse.builder()
                .accountId(b.getAccountId())
                .accountRole(b.getAccountRole().name())
                .balance(b.getBalance())
                .build();
    }
}