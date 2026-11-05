package com.marketplace.backend.dto.request.avalanche;

public record MutualRefundSignatureRequest(Long authorizationDeadline, String typedDataDigest, String signature) {
}
