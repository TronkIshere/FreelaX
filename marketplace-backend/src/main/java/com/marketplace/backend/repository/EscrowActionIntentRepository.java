package com.marketplace.backend.repository;

import com.marketplace.backend.entity.EscrowActionIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface EscrowActionIntentRepository extends JpaRepository<EscrowActionIntent, UUID> {
    Optional<EscrowActionIntent> findByBuildSessionId(String buildSessionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EscrowActionIntent> findWithLockById(UUID id);
    Optional<EscrowActionIntent> findFirstByContractIdAndActionAndPartialTransactionIsNotNullAndSignatureIsNullOrderByCreatedAtDesc(
            UUID contractId, String action);
    Optional<EscrowActionIntent> findFirstByMilestoneIdAndActionAndPayloadHashOrderByCreatedAtDesc(
            UUID milestoneId, String action, String payloadHash);
}
