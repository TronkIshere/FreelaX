package com.paypal.backend.repository;

import com.paypal.backend.entity.PaypalPayoutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaypalPayoutTransactionRepository extends JpaRepository<PaypalPayoutTransaction, UUID> {

    Optional<PaypalPayoutTransaction> findByPlatformPayoutId(String platformPayoutId);

    boolean existsByPlatformPayoutId(String platformPayoutId);
}
