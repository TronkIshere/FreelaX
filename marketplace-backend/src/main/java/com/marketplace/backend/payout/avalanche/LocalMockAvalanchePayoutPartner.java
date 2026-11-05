package com.marketplace.backend.payout.avalanche;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * LABELLED MOCK, LOCAL_SIMULATION only. Supports only chain id 31337 (Anvil) and the single token address it is explicitly
 * configured with (the simulated USDC of the local deployment); with no token configured it supports nothing. Reports no fee and no rate (it is not a quote source), and reports the fiat
 * payout as PAID with a {@code SIMULATED-} reference once asked after a final deposit. No real money is involved anywhere.
 */
public class LocalMockAvalanchePayoutPartner implements AvalanchePayoutPartner {

    public static final String ID = "local-mock";
    public static final long LOCAL_CHAIN_ID = 31337;

    private final String tokenAddress;
    private final String depositAddress;
    private final Clock clock;

    public LocalMockAvalanchePayoutPartner(String tokenAddress, String depositAddress, Clock clock) {
        if (tokenAddress != null && !tokenAddress.isBlank() && !tokenAddress.matches("0x[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("local-mock payout token address must be a 20-byte hex address");
        }
        if (depositAddress == null || !depositAddress.matches("0x[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("local-mock payout partner needs a deposit address");
        }
        // blank = no token explicitly supported: every capability check is then unsupported (explicit support only)
        this.tokenAddress = tokenAddress == null || tokenAddress.isBlank() ? null : tokenAddress.toLowerCase(Locale.ROOT);
        this.depositAddress = depositAddress.toLowerCase(Locale.ROOT);
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean simulated() {
        return true;
    }

    @Override
    public Capability capability(long chainId, String token) {
        if (chainId != LOCAL_CHAIN_ID) {
            return Capability.unavailable("PARTNER_NETWORK_UNSUPPORTED");
        }
        if (tokenAddress == null) {
            return Capability.unavailable("PARTNER_TOKEN_NOT_CONFIGURED");
        }
        if (token == null || !tokenAddress.equalsIgnoreCase(token)) {
            return Capability.unavailable("PARTNER_TOKEN_UNSUPPORTED");
        }
        return Capability.available();
    }

    @Override
    public Order createOrder(OrderRequest request) {
        if (!capability(request.chainId(), request.tokenAddress()).supported()) {
            throw new IllegalArgumentException("unsupported (chain, token) for the local mock");
        }
        return new Order("SIMULATED-" + UUID.nameUUIDFromBytes(request.reference().getBytes(StandardCharsets.UTF_8)), depositAddress, null, null,
                clock.instant().plus(Duration.ofHours(24)));
    }

    @Override
    public FiatStatus fiatStatus(String partnerOrderId, String transferTxHash) {
        return new FiatStatus(FiatState.PAID, "SIMULATED-PAYOUT-" + partnerOrderId.substring("SIMULATED-".length()));
    }
}
