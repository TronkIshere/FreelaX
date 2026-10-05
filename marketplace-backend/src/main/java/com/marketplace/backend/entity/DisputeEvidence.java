package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "dispute_evidence", indexes = @Index(name = "idx_dispute_evidence", columnList = "dispute_id"))
public class DisputeEvidence extends AbstractEntity<UUID> {
    public enum Kind { TEXT, LINK }
    @Column(name = "dispute_id", nullable = false, updatable = false)
    private UUID disputeId;
    @Column(nullable = false, updatable = false)
    private UUID actorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 12)
    private Kind kind;
    @Column(name = "evidence_text", updatable = false, length = 2000)
    private String text;
    @Column(updatable = false, length = 2048)
    private String url;
    @Column(updatable = false, length = 64)
    private String sha256;
}
