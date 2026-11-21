package com.marketplace.backend.repository;

import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.entity.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

public interface WorkContractRepository extends JpaRepository<WorkContract, UUID> {
    Optional<WorkContract> findByJobId(UUID jobId);
    List<WorkContract> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            ContractStatus status, LocalDateTime createdAt);
    /** Released Avalanche contracts without a withholding record yet (owner decision 2026-11-21). */
    @Query("""
            select c.id from WorkContract c
            where c.status = com.marketplace.backend.entity.ContractStatus.COMPLETED
            and lower(c.paymentNetworkId) like 'avalanche%'
            and not exists (select t.id from TaxCertificateRecord t where t.jobId = c.jobId)
            order by c.updatedAt asc, c.id asc
            """)
    List<UUID> findReleasedAvalancheWithoutTaxRecord(org.springframework.data.domain.Pageable pageable);
    long countByFreelancerIdAndStatus(UUID freelancerId, ContractStatus status);
    long countByClientUserIdAndStatus(UUID clientUserId, ContractStatus status);
    @Query("select count(d) from ContractDispute d, WorkContract c where d.contractId = c.id and c.freelancerId = :userId")
    long countFreelancerDisputes(@Param("userId") UUID userId);
    @Query("select count(d) from ContractDispute d, WorkContract c where d.contractId = c.id and c.clientUserId = :userId")
    long countClientDisputes(@Param("userId") UUID userId);
}
