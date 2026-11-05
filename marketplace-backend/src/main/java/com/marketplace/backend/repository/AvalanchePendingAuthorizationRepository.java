package com.marketplace.backend.repository;

import com.marketplace.backend.entity.AvalanchePendingAuthorization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface AvalanchePendingAuthorizationRepository extends JpaRepository<AvalanchePendingAuthorization, Long> {
    Optional<AvalanchePendingAuthorization> findByContractIdAndAction(UUID contractId, String action);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AvalanchePendingAuthorization> findWithLockByContractIdAndAction(UUID contractId, String action);
}
