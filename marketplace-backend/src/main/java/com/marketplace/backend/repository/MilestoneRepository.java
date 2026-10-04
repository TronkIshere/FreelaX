package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    Optional<Milestone> findByContractId(UUID contractId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Milestone> findWithLockById(UUID id);
}
