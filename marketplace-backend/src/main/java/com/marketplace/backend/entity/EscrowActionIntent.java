package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "escrow_action_intents", uniqueConstraints =
        @UniqueConstraint(columnNames = "build_session_id"),
        indexes = @Index(name = "idx_escrow_action_contract", columnList = "contract_id"))
public class EscrowActionIntent extends AbstractEntity<UUID> {
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(name = "milestone_id", nullable = false)
    private UUID milestoneId;
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;
    @Column(nullable = false, length = 30)
    private String action;
    @Column(name = "build_session_id", nullable = false, length = 64)
    private String buildSessionId;
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;
    @Column(name = "original_transaction", columnDefinition = "TEXT")
    private String originalTransaction;
    @Column(name = "partial_transaction", columnDefinition = "TEXT")
    private String partialTransaction;
    @Column(length = 100)
    private String signature;
}
