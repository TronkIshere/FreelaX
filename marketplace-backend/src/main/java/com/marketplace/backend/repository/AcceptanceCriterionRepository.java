package com.marketplace.backend.repository;

import com.marketplace.backend.entity.AcceptanceCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AcceptanceCriterionRepository extends JpaRepository<AcceptanceCriterion, UUID> {
    List<AcceptanceCriterion> findByJobIdOrderByOrderAsc(UUID jobId);
    List<AcceptanceCriterion> findByContractIdOrderByOrderAsc(UUID contractId);
}
