package com.marketplace.backend.repository;

import com.marketplace.backend.entity.WalletLinkChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface WalletLinkChallengeRepository extends JpaRepository<WalletLinkChallenge, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletLinkChallenge> findWithLockById(UUID id);
}
