package com.paypal.backend.repository;

import com.paypal.backend.entity.PaypalAccountBalance;
import com.paypal.backend.entity.PaypalAccountRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaypalAccountBalanceRepository extends JpaRepository<PaypalAccountBalance, UUID> {

    Optional<PaypalAccountBalance> findByAccountIdAndAccountRole(UUID accountId, PaypalAccountRole accountRole);
}