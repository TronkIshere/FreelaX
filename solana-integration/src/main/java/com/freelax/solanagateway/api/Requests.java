package com.freelax.solanagateway.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static com.freelax.solanagateway.api.ApiTypes.Commitment;
import static com.freelax.solanagateway.api.ApiTypes.ExecutionMode;

public final class Requests {

    private Requests() {
    }

    public interface TransactionOptions {
        ExecutionMode mode();
        Commitment commitment();
        Boolean skipPreflight();
    }

    public record InitializeConfigRequest(
            @NotBlank String admin,
            @NotBlank String acceptedMint,
            @NotBlank String treasuryAuthority,
            @NotBlank String rateAuthority,
            @NotBlank String oracleAuthority,
            @NotBlank String maxRateAgeSeconds,
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record UpdateConfigRequest(
            @NotBlank String admin,
            @NotBlank String acceptedMint,
            @NotBlank String treasuryAuthority,
            @NotBlank String rateAuthority,
            @NotBlank String oracleAuthority,
            @NotBlank String maxRateAgeSeconds,
            boolean paused,
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record ConfigureMockOnrampRequest(
            @NotBlank String admin,
            @NotBlank String authority,
            @NotBlank String maxAmount,
            boolean enabled,
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record MockOnrampRequest(
            @NotBlank String onrampAuthority,
            @NotBlank String client,
            @NotBlank String purchaseId,
            @NotBlank String usdAmountE6,
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record PublishRateRequest(
            @NotBlank String rateAuthority,
            @NotBlank String rateId,
            @NotBlank String usdcUsdE6,
            @NotBlank String usdVndE6,
            @NotBlank String observedAt,
            @NotBlank String expiresAt,
            @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{64}") String sourceHash,
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record CreateInvoiceRequest(
            @NotBlank String freelancer,
            @NotBlank String invoiceId,
            @NotBlank String client,
            @NotBlank String amount,
            @NotBlank String rateId,
            @NotBlank String expiresAt,
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record PayInvoiceRequest(
            @NotBlank String client,
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record InvoiceActionRequest(
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record RequestOfframpRequest(
            @NotBlank String freelancer,
            @NotBlank String withdrawalId,
            @NotBlank String rateId,
            @NotBlank String tokenAmount,
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record CompleteOfframpRequest(
            @NotBlank String oracleAuthority,
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record MarkOfframpFailedRequest(
            @NotBlank String oracleAuthority,
            @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{64}") String failureHash,
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record ResolveOfframpRequest(
            @NotBlank String admin,
            @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{64}") String resolutionHash,
            ExecutionMode mode,
            Commitment commitment,
            Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record SubmitSignedTransactionRequest(
            String buildSessionId,
            @NotBlank String transactionBase64,
            Boolean skipPreflight,
            Commitment preflightCommitment
    ) {
    }

    public record FundEscrowRequest(
            @NotBlank String client, @NotBlank String freelancer,
            @NotBlank String amount, @NotBlank String fundingExpiresAt,
            @NotBlank String deliveryDueAt,
            int reviewWindowHours, int maxRevisions,
            ExecutionMode mode, Commitment commitment, Boolean skipPreflight,
            Boolean highValueReviewGrace
    ) implements TransactionOptions {
    }

    public record EscrowActionRequest(
            @NotBlank String actor, String hash, String newDueAt,
            ExecutionMode mode, Commitment commitment, Boolean skipPreflight
    ) implements TransactionOptions {
    }

    public record EscrowMutualRefundRequest(
            @NotBlank String client, @NotBlank String freelancer,
            ExecutionMode mode, Commitment commitment, Boolean skipPreflight
    ) implements TransactionOptions {
    }
}
