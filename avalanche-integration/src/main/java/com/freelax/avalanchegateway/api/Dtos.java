package com.freelax.avalanchegateway.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Wire types. Responses always say which environment they come from and that the money is simulated. */
public final class Dtos {

    private Dtos() {
    }

    public record ConfigResponse(String environment, boolean isSimulated, long chainId, String escrow, String token, String tokenKind,
                                 String contractVersion, boolean paused, String defaultArbiter, boolean backendSignersAvailable,
                                 String explorerBaseUrl) {
    }

    public record BalanceResponse(String environment, boolean isSimulated, String address, String token, String balanceBaseUnits, int decimals) {
    }

    public record AddressValidation(String address, boolean valid) {
    }

    public record EscrowResponse(String environment, boolean isSimulated, long chainId, String escrowId, String status, String client,
                                 String freelancer, String arbiter, String amountBaseUnits, int maxRevisions, int revisionsUsed,
                                 boolean extensionUsed, long reviewWindowSeconds, long submissionCount, long authNonce, long deliveryDueAt,
                                 long originalDeliveryDueAt, long requestedDeliveryDueAt, long reviewDueAt, String submissionHash,
                                 String resolutionHash, String disputedBy, long disputedAt) {
    }

    public record PreparedTx(String purpose, String to, String data, String value) {
    }

    public record PreparedActionResponse(String environment, boolean isSimulated, long chainId, String action, String escrowId,
                                         List<PreparedTx> transactions, Long authorizationDeadline, String typedData, String typedDataDigest) {
    }

    public record TransactionResponse(String environment, boolean isSimulated, long chainId, String operationKey, String state,
                                      String txHash, Long nonce, Long blockNumber, String errorClass, String signerRole) {
    }

    public record HashStatusResponse(String environment, boolean isSimulated, long chainId, String txHash, String state, Long blockNumber) {
    }

    public record FaucetResponse(String environment, boolean isSimulated, String address, String amountBaseUnits, TransactionResponse transaction) {
    }

    /** Unknown properties are rejected: a caller can never smuggle in a key, an RPC URL or a chain id. */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ActionRequest(String operationKey, String client, String freelancer, String amount, Long fundingExpiresAt, Long deliveryDueAt,
                                Integer reviewWindowHours, Integer maxRevisions, Boolean highValueGrace, String evidenceHash,
                                String feedbackHash, String reasonHash, String resolutionHash, Boolean releaseToFreelancer,
                                Long newDeliveryDueAt, String clientSignature, String freelancerSignature, String by) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record FaucetRequest(String address, String amount) {
    }
}
