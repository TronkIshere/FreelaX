package com.marketplace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "avalanche_chain_transactions")
public class AvalancheChainTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(name = "milestone_id")
    private UUID milestoneId;
    @Column(name = "network_id", nullable = false, length = 40)
    private String networkId;
    @Column(nullable = false, length = 32)
    private String environment;
    @Column(name = "chain_id", nullable = false)
    private long chainId;
    @Column(name = "escrow_id", nullable = false, length = 66)
    private String escrowId;
    @Column(nullable = false, length = 30)
    private String action;
    @Column(name = "operation_key", nullable = false, length = 190)
    private String operationKey;
    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;
    @Column(name = "tx_hash", length = 66)
    private String txHash;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "error_class", length = 64)
    private String errorClass;
    @Column(name = "business_applied", nullable = false)
    private boolean businessApplied;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
