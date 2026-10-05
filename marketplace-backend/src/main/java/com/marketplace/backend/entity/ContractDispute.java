package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "contract_disputes", indexes = @Index(name = "idx_dispute_contract_status", columnList = "contract_id,status"))
public class ContractDispute extends AbstractEntity<UUID> {
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "opened_by", nullable = false)
    private UUID openedBy;

    @Column(nullable = false, length = 60)
    private String reasonCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DisputeStatus status;
}
