package com.misa.backend.repository;

import com.misa.backend.entity.PayoutTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface PayoutTransactionRepository extends JpaRepository<PayoutTransaction, UUID> {

    Optional<PayoutTransaction> findByPlatformPayoutId(String platformPayoutId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PayoutTransaction> findWithLockById(UUID id);

    boolean existsByPlatformPayoutId(String platformPayoutId);
}
