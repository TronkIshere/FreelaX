package com.marketplace.backend.dto.response.avalanche;

public record GatewayPreparedTxResponse(String purpose, String to, String data, String value) {
}
