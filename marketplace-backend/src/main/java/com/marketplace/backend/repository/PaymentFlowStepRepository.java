package com.marketplace.backend.repository;

import com.marketplace.backend.entity.PaymentFlowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface PaymentFlowStepRepository extends JpaRepository<PaymentFlowStep, UUID> {
    List<PaymentFlowStep> findByPaymentFlowIdOrderByCreatedAtAsc(UUID paymentFlowId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentFlowStep> findWithLockByPaymentFlowIdAndKind(UUID paymentFlowId, String kind);
    List<PaymentFlowStep> findTop50ByKindAndStatusInOrderByUpdatedAtAsc(String kind,
            java.util.Collection<String> statuses);
    @Query("""
            select work from PaymentFlowStep work
            where work.kind = 'WORK_ACCEPTED' and work.status <> 'CONFIRMED'
              and exists (select release.id from PaymentFlowStep release
                  where release.paymentFlowId = work.paymentFlowId
                    and release.kind = 'USDC_RELEASE' and release.status = 'CONFIRMED')
            order by work.updatedAt asc
            """)
    List<PaymentFlowStep> findReleasedWorkPending(Pageable page);
}
