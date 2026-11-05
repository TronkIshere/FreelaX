package com.freelax.avalanchegateway.dto.response;

public record FaucetResponse(String environment, boolean isSimulated, String address, String amountBaseUnits, TransactionResponse transaction) {

}
