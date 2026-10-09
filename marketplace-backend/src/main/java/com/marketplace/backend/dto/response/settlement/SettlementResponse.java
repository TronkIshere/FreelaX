package com.marketplace.backend.dto.response.settlement;

import com.marketplace.backend.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Explicit allowlist: no provider payloads, bank details, release key or tax identity.
public record SettlementResponse(UUID contractId, UUID milestoneId, UUID jobId,
        BigDecimal amount, String currency, boolean simulation,
        SettlementMoneyStatus moneyStatus, SettlementStageStatus onChainStatus,
        SettlementStageStatus offRampStatus, SettlementStageStatus taxStatus,
        String releaseReference, String onChainReference, String offRampReference, String taxReference,
        BigDecimal platformFeeUsd, BigDecimal freelancerUsd, BigDecimal lockedUsdVndRate,
        BigDecimal partnerPayoutVnd,
        String onChainError, String offRampError, String taxError,
        boolean retryable, String lastError, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static SettlementResponse from(ContractSettlement s) {
        return new SettlementResponse(s.getContractId(), s.getMilestoneId(), s.getJobId(),
                s.getAmount(), s.getCurrency(), s.isSimulation(), s.getMoneyStatus(),
                s.getOnChainStatus(), s.getOffRampStatus(), s.getTaxStatus(),
                s.getPaymentReleaseReference(), s.getOnChainReference(), s.getOffRampReference(),
                s.getTaxReference(), s.getPlatformFeeUsd(), s.getFreelancerUsd(),
                s.getLockedUsdVndRate(), s.getPartnerPayoutVnd(),
                s.getOnChainError(), s.getOffRampError(), s.getTaxError(),
                s.isRetryable(), s.getLastError(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
