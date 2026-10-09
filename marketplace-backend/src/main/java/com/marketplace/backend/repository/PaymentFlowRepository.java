package com.marketplace.backend.repository;

import com.marketplace.backend.entity.PaymentFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface PaymentFlowRepository extends JpaRepository<PaymentFlow, UUID> {
    java.util.List<PaymentFlow> findTop50ByOrderByCreatedAtDesc();
    /** Unified flows whose VND payout is confirmed but which have no MISA certificate yet. */
    @org.springframework.data.jpa.repository.Query("""
            select f from PaymentFlow f, PaymentFlowStep paid
            where paid.paymentFlowId = f.id and paid.kind = 'VND_PAYOUT' and paid.status = 'CONFIRMED'
              and not exists (select t.id from TaxCertificateRecord t
                  where t.jobId = f.jobId and t.misaCertificateId is not null)
            order by paid.updatedAt asc
            """)
    java.util.List<PaymentFlow> findPaidWithoutCertificate(org.springframework.data.domain.Pageable page);
    Optional<PaymentFlow> findByMilestoneId(UUID milestoneId);
    Optional<PaymentFlow> findByContractId(UUID contractId);
    java.util.List<PaymentFlow> findByClientId(UUID clientId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentFlow> findWithLockByMilestoneId(UUID milestoneId);
}
