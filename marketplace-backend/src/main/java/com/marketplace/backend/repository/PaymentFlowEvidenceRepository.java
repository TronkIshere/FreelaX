package com.marketplace.backend.repository;

import com.marketplace.backend.entity.PaymentFlowEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentFlowEvidenceRepository extends JpaRepository<PaymentFlowEvidence, UUID> {
    List<PaymentFlowEvidence> findByPaymentFlowIdOrderByCreatedAtAsc(UUID paymentFlowId);
}
