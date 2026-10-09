package com.payment.backend.repository;

import com.payment.backend.entity.UnifiedUsdOrderMock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnifiedUsdOrderMockRepository extends JpaRepository<UnifiedUsdOrderMock, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UnifiedUsdOrderMock> findWithLockByPaymentFlowId(UUID paymentFlowId);
    List<UnifiedUsdOrderMock> findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            String status, Instant before);
}
