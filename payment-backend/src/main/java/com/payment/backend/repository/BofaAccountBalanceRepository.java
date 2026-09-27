package com.payment.backend.repository;

import com.payment.backend.entity.BofaAccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BofaAccountBalanceRepository extends JpaRepository<BofaAccountBalance, UUID> {

    Optional<BofaAccountBalance> findByBankAccountNumber(String bankAccountNumber);
}