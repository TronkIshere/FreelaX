package com.payment.backend.repository;

import com.payment.backend.entity.PartnerStatementMock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PartnerStatementMockRepository extends JpaRepository<PartnerStatementMock, UUID> {
    List<PartnerStatementMock> findByMilestoneIdOrderByOccurredAtAsc(UUID milestoneId);
    @Query("select sum(s.deltaUsd) from PartnerStatementMock s")
    BigDecimal totalUsd();
}
