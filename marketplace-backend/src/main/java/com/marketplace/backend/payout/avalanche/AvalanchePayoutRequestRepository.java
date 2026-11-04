package com.marketplace.backend.payout.avalanche;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AvalanchePayoutRequestRepository extends JpaRepository<AvalanchePayoutRequest, UUID> {
    Optional<AvalanchePayoutRequest> findByNetworkIdAndMilestoneId(String networkId, UUID milestoneId);

    Optional<AvalanchePayoutRequest> findByNetworkIdAndTransferTxHash(String networkId, String transferTxHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AvalanchePayoutRequest p where p.id = :id")
    Optional<AvalanchePayoutRequest> findWithLockById(@Param("id") UUID id);
}
