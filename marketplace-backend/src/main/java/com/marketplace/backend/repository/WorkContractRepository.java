package com.marketplace.backend.repository;

import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.entity.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkContractRepository extends JpaRepository<WorkContract, UUID> {
    Optional<WorkContract> findByJobId(UUID jobId);
    long countByFreelancerIdAndStatus(UUID freelancerId, ContractStatus status);
    long countByClientUserIdAndStatus(UUID clientUserId, ContractStatus status);
    @Query("select count(d) from ContractDispute d, WorkContract c where d.contractId = c.id and c.freelancerId = :userId")
    long countFreelancerDisputes(@Param("userId") UUID userId);
    @Query("select count(d) from ContractDispute d, WorkContract c where d.contractId = c.id and c.clientUserId = :userId")
    long countClientDisputes(@Param("userId") UUID userId);
}
