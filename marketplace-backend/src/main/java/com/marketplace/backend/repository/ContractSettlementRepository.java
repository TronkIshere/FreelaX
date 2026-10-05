package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ContractSettlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface ContractSettlementRepository extends JpaRepository<ContractSettlement, UUID> {
    Optional<ContractSettlement> findByMilestoneId(UUID milestoneId);
    Optional<ContractSettlement> findByContractId(UUID contractId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ContractSettlement> findWithLockById(UUID id);

    @Query("select s.milestoneId from ContractSettlement s where s.id = :id")
    Optional<UUID> findMilestoneId(@Param("id") UUID id);

    @Query("""
            select s.id from ContractSettlement s where s.retryable = true and s.nextAttemptAt <= :now
            order by s.nextAttemptAt asc, s.id asc
            """)
    List<UUID> findDueIds(@Param("now") Instant now, Pageable pageable);
}
