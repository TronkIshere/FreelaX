package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ContractDispute;
import com.marketplace.backend.entity.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ContractDisputeRepository extends JpaRepository<ContractDispute, UUID> {
    boolean existsByContractIdAndStatusIn(UUID contractId, Collection<DisputeStatus> statuses);
    Optional<ContractDispute> findFirstByContractIdAndStatusInOrderByCreatedAtDesc(
            UUID contractId, Collection<DisputeStatus> statuses);
}
