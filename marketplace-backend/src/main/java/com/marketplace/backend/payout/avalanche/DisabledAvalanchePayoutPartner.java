package com.marketplace.backend.payout.avalanche;

/** Default partner: no capability for anything, so every request ends in an explicit UNSUPPORTED_RAIL result. */
public class DisabledAvalanchePayoutPartner implements AvalanchePayoutPartner {

    public static final String ID = "none";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean simulated() {
        return false;
    }

    @Override
    public Capability capability(long chainId, String tokenAddress) {
        return Capability.unavailable("PARTNER_NOT_CONFIGURED");
    }

    @Override
    public Order createOrder(OrderRequest request) {
        throw new IllegalStateException("no Avalanche payout partner is configured");
    }

    @Override
    public FiatStatus fiatStatus(String partnerOrderId, String transferTxHash) {
        throw new IllegalStateException("no Avalanche payout partner is configured");
    }
}
