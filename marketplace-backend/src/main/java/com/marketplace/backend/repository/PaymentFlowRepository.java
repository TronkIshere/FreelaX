package com.marketplace.backend.repository;

import com.marketplace.backend.entity.PaymentFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface PaymentFlowRepository extends JpaRepository<PaymentFlow, UUID> {
    java.util.List<PaymentFlow> findTop50ByOrderByCreatedAtDesc();
    Optional<PaymentFlow> findByMilestoneId(UUID milestoneId);
    Optional<PaymentFlow> findByContractId(UUID contractId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentFlow> findWithLockByMilestoneId(UUID milestoneId);
}
