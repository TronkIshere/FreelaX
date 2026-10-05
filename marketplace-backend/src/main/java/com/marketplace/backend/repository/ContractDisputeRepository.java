package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ContractDispute;
import com.marketplace.backend.entity.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractDisputeRepository extends JpaRepository<ContractDispute, UUID> {
    Optional<ContractDispute> findByContractId(UUID contractId);
    Page<ContractDispute> findByStatusOrderByCreatedAtAsc(DisputeStatus status, Pageable pageable);
    @Query("select d.id from ContractDispute d where d.status = com.marketplace.backend.entity.DisputeStatus.DECISION_PENDING_REFUND and d.retryable = true and d.nextAttemptAt <= :now order by d.nextAttemptAt, d.id")
    List<UUID> findDueRefundIds(@Param("now") Instant now, Pageable pageable);
    boolean existsByContractIdAndStatusIn(UUID contractId, Collection<DisputeStatus> statuses);
    Optional<ContractDispute> findFirstByContractIdAndStatusInOrderByCreatedAtDesc(
            UUID contractId, Collection<DisputeStatus> statuses);
}
