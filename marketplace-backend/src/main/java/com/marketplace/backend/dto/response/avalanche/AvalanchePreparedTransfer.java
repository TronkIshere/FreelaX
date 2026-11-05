package com.marketplace.backend.dto.response.avalanche;

public record AvalanchePreparedTransfer(long chainId, String to, String data, String value) {
}
