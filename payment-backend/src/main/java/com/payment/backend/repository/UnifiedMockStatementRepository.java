package com.payment.backend.repository;

import com.payment.backend.entity.UnifiedMockStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnifiedMockStatementRepository extends JpaRepository<UnifiedMockStatement, String> {
    List<UnifiedMockStatement> findByPaymentFlowIdOrderByOccurredAtAsc(UUID paymentFlowId);
}
