package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "wallet_link_challenges")
public class WalletLinkChallenge extends AbstractEntity<UUID> {
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 64)
    private String walletAddress;
    @Column(nullable = false, length = 400)
    private String message;
    @Column(nullable = false)
    private Instant expiresAt;
    private Instant usedAt;
}
