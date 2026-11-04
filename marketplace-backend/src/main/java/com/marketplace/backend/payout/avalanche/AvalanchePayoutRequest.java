package com.marketplace.backend.payout.avalanche;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One non-custodial Avalanche payout request (V8). Amounts are token base units (6 decimals) as decimal strings. */
@Getter
@Setter
@Entity
@Table(name = "avalanche_payout_requests")
public class AvalanchePayoutRequest {
    @Id
    private UUID id;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(name = "milestone_id", nullable = false)
    private UUID milestoneId;
    @Column(name = "freelancer_user_id", nullable = false)
    private UUID freelancerUserId;
    @Column(name = "network_id", nullable = false, length = 40)
    private String networkId;
    @Column(nullable = false, length = 32)
    private String environment;
    @Column(name = "chain_id", nullable = false)
    private long chainId;
    @Column(name = "token_address", nullable = false, length = 42)
    private String tokenAddress;
    @Column(name = "escrow_id", nullable = false, length = 66)
    private String escrowId;
    @Column(name = "amount_base_units", nullable = false, length = 78)
    private String amountBaseUnits;
    @Column(name = "from_address", nullable = false, length = 42)
    private String fromAddress;
    @Column(name = "partner_id", nullable = false, length = 40)
    private String partnerId;
    @Column(name = "partner_order_id", nullable = false, length = 100)
    private String partnerOrderId;
    @Column(name = "deposit_address", nullable = false, length = 42)
    private String depositAddress;
    @Column(name = "partner_simulated", nullable = false)
    private boolean partnerSimulated;
    @Column(name = "partner_reported_fee", length = 40)
    private String partnerReportedFee;
    @Column(name = "partner_reported_rate", length = 40)
    private String partnerReportedRate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AvalanchePayoutStatus status;
    @Column(name = "transfer_tx_hash", length = 66)
    private String transferTxHash;
    @Column(name = "transfer_chain_state", length = 16)
    private String transferChainState;
    @Column(name = "transfer_match", length = 32)
    private String transferMatch;
    @Column(name = "transfer_confirmed_at")
    private Instant transferConfirmedAt;
    @Column(name = "fiat_status", length = 16)
    private String fiatStatus;
    @Column(name = "fiat_reference", length = 100)
    private String fiatReference;
    @Column(name = "fiat_confirmed_at")
    private Instant fiatConfirmedAt;
    @Column(name = "failure_reason", length = 64)
    private String failureReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Guarded transition: an illegal move is a programming error and never persists. */
    public void moveTo(AvalanchePayoutStatus next) {
        if (status != null && !status.canMoveTo(next)) {
            throw new IllegalStateException("illegal payout transition " + status + " -> " + next);
        }
        status = next;
    }
}
