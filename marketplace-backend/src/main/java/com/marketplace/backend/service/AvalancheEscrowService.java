package com.marketplace.backend.service;

import com.marketplace.backend.blockchain.PreparedAction;
import com.marketplace.backend.dto.request.avalanche.AvalancheActionInput;
import com.marketplace.backend.dto.response.avalanche.AvalancheContractView;
import com.marketplace.backend.entity.AvalancheChainTransaction;

import java.util.UUID;

/**
 * The Avalanche rail of a contract. Authorization is enforced by the service, before anything reaches the gateway. Business state
 * changes only when a transaction is FINALIZED and the chain state agrees with the records. A contract that already has a Solana
 * escrow is never moved (in-flight Solana escrows finish on Solana).
 */
public interface AvalancheEscrowService {

    /** Opts a contract into the Avalanche rail. Refused for anything that already moved money or already has a Solana escrow. */
    AvalancheContractView enable(UUID userId, UUID contractId);

    AvalancheContractView status(UUID userId, boolean admin, UUID contractId);

    PreparedAction prepare(UUID userId, boolean admin, UUID contractId, String action, AvalancheActionInput input);

    /** Executes with the gateway's LOCAL development wallets. The attempt is recorded BEFORE the gateway is called. */
    AvalancheChainTransaction execute(UUID userId, boolean admin, UUID contractId, String action, AvalancheActionInput input);

    /** Records a transaction the user's own wallet broadcast, after checking the user may perform that action. */
    AvalancheChainTransaction recordUserTransaction(UUID userId, boolean admin, UUID contractId, String action, String txHash,
                                                    AvalancheActionInput input);

    /** Refreshes unfinished transactions from the gateway and applies business effects of FINALIZED ones, once. */
    AvalancheContractView refresh(UUID userId, boolean admin, UUID contractId);

    /**
     * F13: stores the caller's signature of the mutual-refund typed data. The deadline and digest are re-derived from the gateway,
     * so a party can only sign what the escrow currently expects. Each party can write only its own signature.
     */
    AvalancheContractView submitMutualRefundSignature(UUID userId, UUID contractId, Long authorizationDeadline, String typedDataDigest,
                                                      String signature);

    /** R12: stores the caller's signature of a mutual-settlement proposal for a recorded, unresolved dispute. */
    AvalancheContractView submitMutualSettlementSignature(UUID userId, UUID contractId, UUID disputeId, Boolean releaseToFreelancer,
                                                          Long authorizationDeadline, String typedDataDigest, String signature);
}
