package com.marketplace.backend.payout.avalanche;

import java.time.Instant;

/**
 * Port to an off-ramp/payout partner for the Avalanche rail. Independent of the Solana payout pipeline (no shared state, no
 * fallback between chains). The partner, not FreelaX, quotes any fee or rate: FreelaX invents no fee formula and no tax rule;
 * whatever a partner reports is stored as reported. Real partners are NOT integrated: they need partner support for the
 * exact (chain id, token contract) pair plus external authorization first.
 */
public interface AvalanchePayoutPartner {

    /** Stable identifier persisted with each request ({@code none}, {@code local-mock}, ...). */
    String id();

    /** True only for the labelled LOCAL mock; recorded on every request so a simulated payout can never read as real. */
    boolean simulated();

    /** Whether the partner supports paying out this exact token contract on this exact chain. Never matched by symbol. */
    Capability capability(long chainId, String tokenAddress);

    /** Creates the partner order and returns the deposit address the freelancer's own wallet must send to. */
    Order createOrder(OrderRequest request);

    /** The partner's fiat payout status for an order whose on-chain deposit is already final. */
    FiatStatus fiatStatus(String partnerOrderId, String transferTxHash);

    record Capability(boolean supported, String reason) {
        public static Capability available() {
            return new Capability(true, null);
        }

        public static Capability unavailable(String reason) {
            return new Capability(false, reason);
        }
    }

    record OrderRequest(String reference, long chainId, String tokenAddress, String amountBaseUnits, String fromAddress) {
    }

    /** {@code partnerReportedFee} and {@code partnerReportedRate} are whatever the partner returns; null when it returns none. */
    record Order(String partnerOrderId, String depositAddress, String partnerReportedFee, String partnerReportedRate, Instant expiresAt) {
    }

    enum FiatState { PENDING, PAID, FAILED }

    record FiatStatus(FiatState state, String reference) {
    }
}
