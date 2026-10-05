package com.marketplace.backend.repository;
import com.marketplace.backend.entity.ContractCancellation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.*;
public interface ContractCancellationRepository extends JpaRepository<ContractCancellation, UUID> {
    Optional<ContractCancellation> findByContractId(UUID contractId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<ContractCancellation> findWithLockById(UUID id);
    @Query("select c.milestoneId from ContractCancellation c where c.id = :id")
    Optional<UUID> findMilestoneId(@Param("id") UUID id);
    @Query("select c.id from ContractCancellation c where c.retryable = true and c.nextAttemptAt <= :now order by c.nextAttemptAt, c.id")
    List<UUID> findDueIds(@Param("now") Instant now, Pageable pageable);
}
