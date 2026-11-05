package com.marketplace.backend.dto.response.avalanche;

public record GatewayBalanceResponse(String environment, boolean isSimulated, String address, String token, String balanceBaseUnits, int decimals) {
}
