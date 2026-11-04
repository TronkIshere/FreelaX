package com.marketplace.backend.payout.avalanche;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of one non-custodial Avalanche payout. Two confirmations are kept apart on purpose:
 * {@link #TRANSFER_CONFIRMED} means only that the freelancer's own on-chain transfer to the partner deposit address is
 * final on chain; {@link #FIAT_PAYOUT_CONFIRMED} means the partner reported the fiat payout as paid. Neither implies the other.
 * <pre>
 * AWAITING_USER_TRANSFER -> TRANSFER_SUBMITTED -> TRANSFER_CONFIRMED -> FIAT_PAYOUT_PENDING -> FIAT_PAYOUT_CONFIRMED
 *                                  |  ^                    |                     |
 *                                  v  | (new hash)         +---------------------+--> FIAT_PAYOUT_FAILED
 *                            TRANSFER_FAILED
 * </pre>
 */
public enum AvalanchePayoutStatus {
    AWAITING_USER_TRANSFER,
    TRANSFER_SUBMITTED,
    TRANSFER_FAILED,
    TRANSFER_CONFIRMED,
    FIAT_PAYOUT_PENDING,
    FIAT_PAYOUT_CONFIRMED,
    FIAT_PAYOUT_FAILED;

    private static final Map<AvalanchePayoutStatus, Set<AvalanchePayoutStatus>> NEXT = Map.of(
            AWAITING_USER_TRANSFER, EnumSet.of(TRANSFER_SUBMITTED),
            TRANSFER_SUBMITTED, EnumSet.of(TRANSFER_CONFIRMED, TRANSFER_FAILED),
            // a definitive on-chain failure (revert, wrong recipient or amount) lets the freelancer submit another transfer
            TRANSFER_FAILED, EnumSet.of(TRANSFER_SUBMITTED),
            TRANSFER_CONFIRMED, EnumSet.of(FIAT_PAYOUT_PENDING, FIAT_PAYOUT_CONFIRMED, FIAT_PAYOUT_FAILED),
            FIAT_PAYOUT_PENDING, EnumSet.of(FIAT_PAYOUT_CONFIRMED, FIAT_PAYOUT_FAILED),
            FIAT_PAYOUT_CONFIRMED, EnumSet.noneOf(AvalanchePayoutStatus.class),
            FIAT_PAYOUT_FAILED, EnumSet.noneOf(AvalanchePayoutStatus.class));

    public boolean canMoveTo(AvalanchePayoutStatus next) {
        return NEXT.get(this).contains(next);
    }

    public boolean terminal() {
        return NEXT.get(this).isEmpty();
    }

    /** True once the freelancer's on-chain transfer is final; says nothing about the fiat payout. */
    public boolean onChainTransferConfirmed() {
        return this == TRANSFER_CONFIRMED || this == FIAT_PAYOUT_PENDING || this == FIAT_PAYOUT_CONFIRMED || this == FIAT_PAYOUT_FAILED;
    }
}
