package com.marketplace.backend.repository;

import com.marketplace.backend.entity.AvalancheChainTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvalancheChainTransactionRepository extends JpaRepository<AvalancheChainTransaction, Long> {
    Optional<AvalancheChainTransaction> findByNetworkIdAndOperationKey(String networkId, String operationKey);

    List<AvalancheChainTransaction> findByContractIdOrderByIdAsc(UUID contractId);

    long countByContractIdAndAction(UUID contractId, String action);
}
