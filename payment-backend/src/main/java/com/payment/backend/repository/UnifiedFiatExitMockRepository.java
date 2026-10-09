package com.payment.backend.repository;

import com.payment.backend.entity.UnifiedFiatExitMock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnifiedFiatExitMockRepository extends JpaRepository<UnifiedFiatExitMock, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UnifiedFiatExitMock> findWithLockByPaymentFlowId(UUID paymentFlowId);
    List<UnifiedFiatExitMock> findTop50ByStatusOrderByCreatedAtAsc(String status);
}
