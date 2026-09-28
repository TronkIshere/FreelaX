package com.marketplace.backend.dto.response.job;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JobPaymentStatusResponse {
    UUID jobId;
    UUID checkoutOrderId;
    String checkoutOrderStatus;
    String taxExportStatus;
    Boolean simulation;
    String network;
    String onRampStatus;
    String offRampStatus;
    String onRampClientPublicKey;
    String onRampPurchaseId;
    String onRampTransactionSignature;
    String onRampReceiptPda;
    String explorerUrl;
    BigDecimal amountUsdcReceived;
    String clientPaymentStatus;
    String freelancerPublicKey;
    String rateId;
    String rateTransactionSignature;
    String rateSnapshotPda;
    String invoiceId;
    String invoicePda;
    String paymentMint;
    String invoiceTransactionSignature;
    String paymentTransactionSignature;
    String paymentExplorerUrl;
    LocalDateTime clientPaymentSubmittedAt;
    LocalDateTime clientPaymentConfirmedAt;
    String clientPaymentError;
    String onChainOffRampStatus;
    String withdrawalId;
    String withdrawalPda;
    String treasuryPublicKey;
    String treasuryUsdcAta;
    String withdrawalTokenAmount;
    String withdrawalFiatAmountVnd;
    String withdrawalTransactionSignature;
    String withdrawalExplorerUrl;
    LocalDateTime withdrawalSubmittedAt;
    LocalDateTime withdrawalConfirmedAt;
    String onChainOffRampError;
    String payoutBankCode;
    String payoutBankAccountNumber;
    String payoutBankAccountHolderName;
    String offRampReference;
    BigDecimal amountVndBeforeOffRampFee;
    BigDecimal offRampFeeVnd;
    LocalDateTime simulatedPayoutAt;
    String offRampCompletionSignature;
    String offRampCompletionExplorerUrl;
    LocalDateTime offRampCompletionSubmittedAt;
    LocalDateTime offRampCompletedAt;
    String offRampError;
    BigDecimal estimatedAmountVnd;
    String usdcToVndRateSource;
    BigDecimal taxableAmountVnd;
    String taxRateSource;
}
