package com.marketplace.backend.payout.avalanche;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Avalanche payout rail (Phase 3, delegated engineering). Defaults keep today's behaviour: disabled, no partner. Every
 * Avalanche payout request then returns an explicit UNSUPPORTED_RAIL result; nothing falls back to the Solana pipeline.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "avalanche-payout")
public class AvalanchePayoutProperties {
    private boolean enabled = false;
    /** {@code none} (default) or {@code local-mock} (LOCAL_SIMULATION only). No real partner exists in this codebase. */
    private String partner = DisabledAvalanchePayoutPartner.ID;
    /** local-mock only: the one token contract it explicitly supports (blank = supports nothing). */
    private String localMockTokenAddress = "";
    /** local-mock only: where the freelancer's own wallet sends the simulated USDC. */
    private String localMockDepositAddress = "0x00000000000000000000000000000000000c0ffe";
}
