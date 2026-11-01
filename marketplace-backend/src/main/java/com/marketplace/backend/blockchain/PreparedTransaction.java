package com.marketplace.backend.blockchain;

public record PreparedTransaction(String chainFormat, String purpose, String to, String data, String value, long chainId) {
}
