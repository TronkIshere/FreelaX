package com.marketplace.backend.repository;

import com.marketplace.backend.entity.FundingStatus;
import com.marketplace.backend.entity.FundingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    List<FundingTransaction> findTop50ByPaymentMethodIdAndStatusInOrderByUpdatedAtAsc(
            String paymentMethodId, Collection<FundingStatus> statuses);
    List<FundingTransaction> findByPaymentMethodIdAndStatus(String paymentMethodId, FundingStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FundingTransaction> findWithLockById(UUID id);
    @Query("select count(distinct f.contractId) from FundingTransaction f where f.clientUserId = :userId and f.status = :status")
    long countDistinctContractsByClientAndStatus(@Param("userId") UUID userId, @Param("status") FundingStatus status);
}
