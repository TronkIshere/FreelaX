package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    Optional<Milestone> findByContractId(UUID contractId);
}
