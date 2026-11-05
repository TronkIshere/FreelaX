package com.freelax.avalanchegateway.dto.response;

public record TransactionResponse(String environment, boolean isSimulated, long chainId, String operationKey, String state,
                                  String txHash, Long nonce, Long blockNumber, String errorClass, String signerRole) {

}
