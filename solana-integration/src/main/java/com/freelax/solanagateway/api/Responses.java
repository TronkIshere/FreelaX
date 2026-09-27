package com.freelax.solanagateway.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public final class Responses {

    private Responses() {
    }

    public record DerivedAccounts(
            String config,
            String programData,
            String invoice,
            String rateSnapshot,
            String withdrawalRecord,
            String mockOnrampReceipt,
            String mockOnrampTreasuryAuthority,
            String mockOnrampTreasuryAta,
            String clientAta,
            String freelancerAta,
            String treasuryAta
    ) {
    }

    public sealed interface TransactionOperationResponse
            permits TransactionBuildResponse, TransactionSubmittedResponse {
    }

    public record TransactionBuildResponse(
            String status,
            String instruction,
            String buildSessionId,
            String transactionBase64,
            String feePayer,
            String recentBlockhash,
            long lastValidBlockHeight,
            List<String> requiredSigners,
            DerivedAccounts derivedAccounts
    ) implements TransactionOperationResponse {
    }

    public record TransactionSubmittedResponse(
            String status,
            String instruction,
            String signature,
            DerivedAccounts derivedAccounts
    ) implements TransactionOperationResponse {
    }

    public record AccountResponse<T>(boolean exists, T data) {
    }

    public record ConfigDto(
            String address, String admin, String acceptedMint, String treasuryAuthority,
            String rateAuthority, String oracleAuthority, String maxRateAgeSeconds,
            String mockOnrampAuthority, String maxMockOnrampAmount,
            boolean mockOnrampEnabled, boolean paused, int bump
    ) {
    }

    public record InvoiceDto(
            String address, String invoiceId, String freelancer, String client, String amount,
            String mint, String rateSnapshot, String expiresAt, String status,
            String createdAt, String paidAt, int bump
    ) {
    }

    public record RateSnapshotDto(
            String address, String rateId, String usdcUsdE6, String usdVndE6,
            String usdcVndE6, String observedAt, String expiresAt, String sourceHash,
            String publisher, int bump
    ) {
    }

    public record WithdrawalDto(
            String address, String withdrawalId, String freelancer, String tokenAmount,
            String mint, String treasury, String rateSnapshot, String fiatAmountVnd,
            String status, String requestedAt, String completedAt, String failureHash,
            String failedAt, String resolutionHash, String resolvedAt, String resolvedBy, int bump
    ) {
    }

    public record MockOnrampReceiptDto(
            String address, String purchaseId, String client, String clientAta, String mint,
            String treasury, String usdAmountE6, String tokenAmount, String authority,
            String completedAt, int bump
    ) {
    }

    public record TokenBalanceResponse(
            boolean exists, String ata, String owner, String mint, String amount,
            int decimals, String uiAmountString
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionStatusResponse(
            String signature, boolean found, String slot, Integer confirmations,
            String confirmationStatus, Map<String, Object> error, String blockTime,
            List<String> logs
    ) {
    }

    public record SubmitTransactionResponse(String signature) {
    }
}
