package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    Optional<Milestone> findByContractId(UUID contractId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Milestone> findWithLockById(UUID id);

    @Query("""
            select m.id from Milestone m
            where m.status = com.marketplace.backend.entity.MilestoneStatus.RELEASE_PENDING
            and not exists (select s.id from ContractSettlement s where s.milestoneId = m.id)
            order by m.updatedAt asc, m.id asc
            """)
    List<UUID> findUnsettledReleaseIds(Pageable pageable);
}
