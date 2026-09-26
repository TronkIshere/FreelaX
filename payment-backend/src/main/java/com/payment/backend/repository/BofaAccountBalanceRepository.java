package com.payment.backend.repository;

import com.payment.backend.entity.BofaAccountBalance;
import com.payment.backend.entity.BofaAccountRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BofaAccountBalanceRepository extends JpaRepository<BofaAccountBalance, UUID> {

    Optional<BofaAccountBalance> findByAccountIdAndAccountRole(UUID accountId, BofaAccountRole accountRole);
}