package com.marketplace.backend.blockchain;

public record EnvironmentInfo(String environment, long chainId, boolean simulated, String explorerBaseUrl, boolean backendSignersAvailable) {
}
