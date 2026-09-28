package com.marketplace.backend.dto.response.job;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
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
    BigDecimal estimatedAmountVnd;
    String usdcToVndRateSource;
    BigDecimal taxableAmountVnd;
    String taxRateSource;
}