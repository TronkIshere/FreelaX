package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.avalanche.AvalanchePayoutResult;
import com.marketplace.backend.dto.response.avalanche.AvalanchePayoutView;

import java.util.Optional;
import java.util.UUID;

/**
 * Independent, non-custodial Avalanche payout (Phase 3). After an Avalanche escrow released to the freelancer, the USDC sits in
 * the freelancer's OWN wallet. A payout is: the partner (if it explicitly supports this chain and token) issues a deposit
 * address; the freelancer's own wallet sends the released amount there; the gateway proves the transfer from the receipt; then
 * the partner reports the fiat payout. FreelaX never holds funds, never signs for the user and never routes to another chain.
 * The Solana settlement pipeline is not used or touched.
 */
public interface AvalanchePayoutService {

    AvalanchePayoutResult request(UUID userId, UUID contractId);

    /** The freelancer reports the hash of the transfer their own wallet sent. Only recorded here; verified on refresh. */
    AvalanchePayoutView recordTransfer(UUID userId, UUID payoutId, String txHash);

    /**
     * Advances at most one confirmation per stage: on-chain first (FINALIZED and exactly one matching Transfer), then the
     * partner's fiat status. A gateway or partner outage throws and leaves the record unchanged (retry later).
     */
    AvalanchePayoutView refresh(UUID userId, UUID payoutId);

    Optional<AvalanchePayoutView> forContract(UUID userId, UUID contractId);
}
