package com.misa.backend.repository;

import com.misa.backend.entity.PayoutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayoutTransactionRepository extends JpaRepository<PayoutTransaction, UUID> {

    Optional<PayoutTransaction> findByPlatformPayoutId(String platformPayoutId);

    boolean existsByPlatformPayoutId(String platformPayoutId);
}
