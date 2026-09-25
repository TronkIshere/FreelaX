package com.paypal.backend.repository;

import com.paypal.backend.entity.PaypalAccountBalance;
import com.paypal.backend.entity.PaypalAccountRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaypalAccountBalanceRepository extends JpaRepository<PaypalAccountBalance, UUID> {

    Optional<PaypalAccountBalance> findByAccountIdAndAccountRoleAndCurrency(
            UUID accountId, PaypalAccountRole accountRole, String currency);

    List<PaypalAccountBalance> findByAccountIdAndAccountRole(UUID accountId, PaypalAccountRole accountRole);
}