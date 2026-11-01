package com.marketplace.backend.dto.response.avalanche;

import java.util.List;

public final class AvalancheResults {

    private AvalancheResults() {
    }

    public record Config(String environment, boolean isSimulated, long chainId, String escrow, String token, String tokenKind,
                         String contractVersion, boolean paused, String defaultArbiter, boolean backendSignersAvailable,
                         String explorerBaseUrl) {
    }

    public record Balance(String environment, boolean isSimulated, String address, String token, String balanceBaseUnits, int decimals) {
    }

    public record Escrow(String environment, boolean isSimulated, long chainId, String escrowId, String status, String client, String freelancer,
                         String arbiter, String amountBaseUnits, int maxRevisions, int revisionsUsed, long submissionCount, long deliveryDueAt,
                         long reviewDueAt, String submissionHash, String resolutionHash) {
    }

    public record PreparedTx(String purpose, String to, String data, String value) {
    }

    public record Prepared(String environment, boolean isSimulated, long chainId, String action, String escrowId, List<PreparedTx> transactions,
                           Long authorizationDeadline, String typedData, String typedDataDigest) {
    }

    public record Transaction(String environment, boolean isSimulated, long chainId, String operationKey, String state, String txHash,
                              Long nonce, Long blockNumber, String errorClass, String signerRole) {
    }

    public record HashStatus(String environment, boolean isSimulated, long chainId, String txHash, String state, Long blockNumber) {
    }
}
