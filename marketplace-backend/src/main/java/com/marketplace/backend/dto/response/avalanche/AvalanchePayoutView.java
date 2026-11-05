package com.marketplace.backend.dto.response.avalanche;

import com.marketplace.backend.entity.AvalanchePayoutStatus;
import com.marketplace.backend.entity.ExchangeRateSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AvalanchePayoutView(UUID id, UUID contractId, String networkId, String environment, long chainId, String tokenAddress,
                                  String amountBaseUnits, String fromAddress, String depositAddress, String partnerId, boolean partnerSimulated,
                                  String partnerReportedFee, String partnerReportedRate, AvalanchePayoutStatus status,
                                  String transferTxHash, String transferChainState, String transferMatch,
                                  boolean onChainTransferConfirmed, Instant transferConfirmedAt,
                                  boolean fiatPayoutConfirmed, String fiatReference, Instant fiatConfirmedAt,
                                  String failureReason, String taxTreatment, String notice, AvalanchePreparedTransfer preparedTransfer,
                                  BigDecimal platformFeeUsd, BigDecimal taxUsdToVndRate,
                                  ExchangeRateSource taxRateSource, BigDecimal taxableAmountVnd) {
}
