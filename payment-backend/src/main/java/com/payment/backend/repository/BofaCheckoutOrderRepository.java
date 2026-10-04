package com.payment.backend.repository;

import com.payment.backend.entity.BofaCheckoutOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface BofaCheckoutOrderRepository extends JpaRepository<BofaCheckoutOrder, UUID> {
    Optional<BofaCheckoutOrder> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BofaCheckoutOrder> findWithLockById(UUID id);
}
