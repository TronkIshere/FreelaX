package com.marketplace.backend.repository;

import com.marketplace.backend.entity.FundingStatus;
import com.marketplace.backend.entity.FundingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundingTransactionRepository extends JpaRepository<FundingTransaction, UUID> {
    Optional<FundingTransaction> findByClientUserIdAndIdempotencyKey(UUID clientUserId, String idempotencyKey);
    boolean existsByMilestoneIdAndStatusIn(UUID milestoneId, Collection<FundingStatus> statuses);
    Optional<FundingTransaction> findFirstByMilestoneIdOrderByCreatedAtDesc(UUID milestoneId);
    List<FundingTransaction> findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            Collection<FundingStatus> statuses, LocalDateTime before);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FundingTransaction> findWithLockById(UUID id);
}
