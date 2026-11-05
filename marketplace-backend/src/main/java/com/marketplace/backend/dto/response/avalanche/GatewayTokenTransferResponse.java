package com.marketplace.backend.dto.response.avalanche;

/** Gateway {@code /token-transfers/{hash}}: {@code state} of the transaction and {@code match} of the Transfer log. */
public record GatewayTokenTransferResponse(String environment, boolean isSimulated, long chainId, String txHash, String token, String from, String to,
                            String amountBaseUnits, String state, String match, Long blockNumber) {
}
