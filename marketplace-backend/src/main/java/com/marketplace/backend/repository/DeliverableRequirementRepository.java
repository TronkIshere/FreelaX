package com.marketplace.backend.repository;

import com.marketplace.backend.entity.DeliverableRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliverableRequirementRepository extends JpaRepository<DeliverableRequirement, UUID> {
    List<DeliverableRequirement> findByJobIdOrderByOrderAsc(UUID jobId);
    List<DeliverableRequirement> findByContractIdOrderByOrderAsc(UUID contractId);
}
