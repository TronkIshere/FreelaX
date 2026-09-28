package com.marketplace.backend.provider.currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface OnRampProvider {

    OnRampQuote quote(UUID reference, BigDecimal amountUsd);

    OnRampResult execute(OnRampQuote quote, String recipientPublicKey);

    OnRampResult resume(OnRampQuote quote, String recipientPublicKey, String signature,
                        String clientUsdcAta, String receiptPda, LocalDateTime submittedAt);

    String network();
}