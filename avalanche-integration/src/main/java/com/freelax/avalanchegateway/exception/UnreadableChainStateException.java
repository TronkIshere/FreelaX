package com.freelax.avalanchegateway.exception;

/**
 * The chain answered, but not with what the escrow contract or the token would answer: an empty result, which is what an address without contract
 * code returns (for example after the chain was reset). Not a revert and not an RPC outage; the API reports it as CHAIN_STATE_UNREADABLE.
 */
public class UnreadableChainStateException extends IllegalStateException {
    public UnreadableChainStateException(String message) {
        super(message);
    }
}
