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
                                Long newDeliveryDueAt, String clientSignature, String freelancerSignature, String by,
                                Long authorizationDeadline) {
    }

    /** EIP-191 personal_sign verification for wallet linking (register A8). Strict: unknown properties are rejected. */
    @JsonIgnoreProperties(ignoreUnknown = false)
    /**
     * Read-only check of a token transfer the gateway did not send (Avalanche payout rail). {@code state} is the transaction
     * state (O-10 subset: PENDING, UNKNOWN, REVERTED, CONFIRMED, FINALIZED); {@code match} says whether the receipt holds
     * exactly one Transfer(from, to, amount) of the manifest token. ON-CHAIN evidence only, never a fiat payout confirmation.
     */
    public record TokenTransferResponse(String environment, boolean isSimulated, long chainId, String txHash, String token,
                                        String from, String to, String amountBaseUnits, String state, String match, Long blockNumber) {
    }

    public record WalletSignatureRequest(String address, String message, String signature) {
    }

    public record WalletSignatureResponse(String address, boolean valid) {
    }

    /** P3-C4: stuck-and-reported escrow (O-6). {@code alerts} is empty when nothing blocks the escrow. */
    public record IssuerAlertsResponse(String environment, boolean isSimulated, long chainId, String escrowId, String escrowStatus,
                                       java.util.List<com.freelax.avalanchegateway.contract.IssuerControls.Alert> alerts, String note) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record FaucetRequest(String address, String amount) {
    }
}
