package com.marketplace.backend.repository;

import com.marketplace.backend.entity.EscrowContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EscrowContractRepository extends JpaRepository<EscrowContract, UUID> {
    Optional<EscrowContract> findByContractId(UUID contractId);
    Optional<EscrowContract> findByMilestoneId(UUID milestoneId);
    boolean existsByContractId(UUID contractId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EscrowContract> findWithLockById(UUID id);
    List<EscrowContract> findTop100ByLastChainStatusInOrderByUpdatedAtAsc(Collection<String> statuses);
    @Query("select e.contractId from EscrowContract e where e.releasedAt is not null "
            + "and e.lastChainStatus = 'RELEASED_RECONCILED' "
            + "and not exists (select r.id from ContractReview r where r.contractId = e.contractId)")
    List<UUID> findUninvitedReleasedContractIds(Pageable pageable);
}
