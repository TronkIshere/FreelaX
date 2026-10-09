package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "escrow_contracts", uniqueConstraints = @UniqueConstraint(columnNames = "contract_id"),
        indexes = @Index(name = "idx_escrow_contract_milestone", columnList = "milestone_id"))
public class EscrowContract extends AbstractEntity<UUID> {
    @Version
    private long version;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(name = "milestone_id", nullable = false, unique = true)
    private UUID milestoneId;
    @Column(name = "client_wallet", nullable = false, length = 64)
    private String clientWallet;
    @Column(name = "freelancer_wallet", nullable = false, length = 64)
    private String freelancerWallet;
    @Column(name = "arbiter_wallet", length = 64)
    private String arbiterWallet;
    @Column(name = "escrow_address", nullable = false, length = 64)
    private String escrowAddress;
    @Column(name = "mint", nullable = false, length = 64)
    private String mint;
    @Column(name = "fund_build_session", length = 64)
    private String fundBuildSession;
    @Column(name = "fund_signature", length = 100)
    private String fundSignature;
    @Column(name = "claim_signature", length = 100)
    private String claimSignature;
    @Column(name = "claim_submitted_at")
    private Instant claimSubmittedAt;
    @Column(name = "release_signature", length = 100)
    private String releaseSignature;
    @Column(name = "release_submitted_at")
    private Instant releaseSubmittedAt;
    @Column(name = "resolution_signature", length = 100)
    private String resolutionSignature;
    @Column(name = "resolution_submitted_at")
    private Instant resolutionSubmittedAt;
    @Column(name = "refund_signature", length = 100)
    private String refundSignature;
    @Column(name = "refund_submitted_at")
    private Instant refundSubmittedAt;
    @Column(name = "released_at")
    private Instant releasedAt;
    @Column(name = "last_chain_status", length = 30)
    private String lastChainStatus;
    @Column(name = "settlement_retry_pending")
    private boolean settlementRetryPending;
}
