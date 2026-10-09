package com.marketplace.backend.dto.response.partner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PartnerEscrowResult(UUID milestoneId, UUID contractId, UUID jobId, UUID clientId,
                                  UUID freelancerId, BigDecimal grossUsd, String status,
                                  BigDecimal feeUsd, BigDecimal freelancerUsd, BigDecimal usdVndRate,
                                  BigDecimal payoutVnd, String releaseKey, String refundKey,
                                  String recipientBankCode, String recipientBankLast4,
                                  Instant rateLockedAt, Instant updatedAt, boolean simulation) {}
