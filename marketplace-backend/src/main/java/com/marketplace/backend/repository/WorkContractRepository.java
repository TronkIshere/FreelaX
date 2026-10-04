package com.marketplace.backend.repository;

import com.marketplace.backend.entity.WorkContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkContractRepository extends JpaRepository<WorkContract, UUID> {
    Optional<WorkContract> findByJobId(UUID jobId);
}
