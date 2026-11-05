package com.freelax.avalanchegateway.dto.response;

/** Read-only check of a token transfer the gateway did not send (Avalanche payout rail). {@code state} is the transaction
 * state (O-10 subset: PENDING, UNKNOWN, REVERTED, CONFIRMED, FINALIZED); {@code match} says whether the receipt holds
 * exactly one Transfer(from, to, amount) of the manifest token. ON-CHAIN evidence only, never a fiat payout confirmation. */
public record TokenTransferResponse(String environment, boolean isSimulated, long chainId, String txHash, String token,
                                    String from, String to, String amountBaseUnits, String state, String match, Long blockNumber) {

}
