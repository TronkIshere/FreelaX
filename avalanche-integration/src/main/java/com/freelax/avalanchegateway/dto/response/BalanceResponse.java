package com.freelax.avalanchegateway.dto.response;

public record BalanceResponse(String environment, boolean isSimulated, String address, String token, String balanceBaseUnits, int decimals) {

}
