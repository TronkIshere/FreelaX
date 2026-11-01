package com.marketplace.backend.blockchain;

public record TransactionResult(TransactionReference reference, TransactionStatus status, String transactionHash, Long blockNumber,
                                String errorClass) {
}
