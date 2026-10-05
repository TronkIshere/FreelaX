package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "dispute_evidence_batches", uniqueConstraints =
        @UniqueConstraint(name = "uk_dispute_evidence_batch_key",
                columnNames = {"dispute_id", "actor_id", "idempotency_key"}))
public class DisputeEvidenceBatch extends AbstractEntity<UUID> {
    @Column(name = "dispute_id", nullable = false, updatable = false)
    private UUID disputeId;
    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;
    @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
    private String payloadHash;
}
