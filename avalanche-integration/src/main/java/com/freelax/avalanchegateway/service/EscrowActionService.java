package com.freelax.avalanchegateway.service;

import com.freelax.avalanchegateway.dto.request.ActionRequest;
import com.freelax.avalanchegateway.dto.request.FaucetRequest;
import com.freelax.avalanchegateway.dto.response.FaucetResponse;
import com.freelax.avalanchegateway.dto.response.PreparedActionResponse;
import com.freelax.avalanchegateway.dto.response.TransactionResponse;

/**
 * Builds the calldata and the authorizations for every escrow action and, in LOCAL_SIMULATION only, executes them with the
 * development wallets (owner decision O-11). Which signer role performs an action is decided by the service from the action,
 * never by the caller.
 */
public interface EscrowActionService {

    /** Calldata (and, where the contract needs one, the backend authorization) for an action. Nothing is sent. */
    PreparedActionResponse prepare(String escrowId, String action, ActionRequest request);

    /** Executes an action with the development wallets. Exists only where backend signers exist (LOCAL_SIMULATION). */
    TransactionResponse execute(String escrowId, String action, ActionRequest request);

    /** Simulation Balance: mints the simulated USDC. Only for the mock token in LOCAL_SIMULATION. */
    FaucetResponse faucet(FaucetRequest request);
}
