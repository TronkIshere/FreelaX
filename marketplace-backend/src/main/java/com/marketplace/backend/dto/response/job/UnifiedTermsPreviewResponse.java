package com.marketplace.backend.dto.response.job;

import java.math.BigDecimal;

/** Read-only local mock terms shown to both sides before assignment. */
public record UnifiedTermsPreviewResponse(String rail, int version, String fingerprint, BigDecimal grossUsd,
        BigDecimal escrowUsdc, BigDecimal platformFeeUsdc, BigDecimal usdcVndRate,
        BigDecimal estimatedPayoutVnd, BigDecimal fullRefundUsd,
        int fundingHours, int reviewWindowHours, int maxRevisions,
        String network, String mint, boolean simulation) { }
