package com.payment.backend.repository;

import com.payment.backend.entity.BofaAccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface BofaAccountBalanceRepository extends JpaRepository<BofaAccountBalance, UUID> {

    Optional<BofaAccountBalance> findByBankAccountNumber(String bankAccountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BofaAccountBalance> findWithLockByBankAccountNumber(String bankAccountNumber);
}
