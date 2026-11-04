package com.marketplace.backend.payout.avalanche;

import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Chooses the payout partner. Fails closed at startup on an unknown partner or on the mock outside the local network. */
@Configuration
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class AvalanchePayoutConfiguration {

    static final String LOCAL_NETWORK = "avalanche-local";

    @Bean
    AvalanchePayoutPartner avalanchePayoutPartner(AvalanchePayoutProperties payout, AvalancheGatewayProperties gateway) {
        return select(payout, gateway, Clock.systemUTC());
    }

    static AvalanchePayoutPartner select(AvalanchePayoutProperties payout, AvalancheGatewayProperties gateway, Clock clock) {
        String partner = payout.getPartner() == null ? "" : payout.getPartner().trim();
        if (DisabledAvalanchePayoutPartner.ID.equals(partner)) {
            return new DisabledAvalanchePayoutPartner();
        }
        if (LocalMockAvalanchePayoutPartner.ID.equals(partner)) {
            if (!LOCAL_NETWORK.equals(gateway.getNetwork())) {
                throw new IllegalStateException("avalanche-payout.partner=local-mock is allowed only with avalanche-gateway.network="
                        + LOCAL_NETWORK + " (LOCAL_SIMULATION)");
            }
            return new LocalMockAvalanchePayoutPartner(payout.getLocalMockTokenAddress(), payout.getLocalMockDepositAddress(), clock);
        }
        throw new IllegalStateException("unknown avalanche-payout.partner '" + partner + "': only 'none' and 'local-mock' exist;"
                + " a real payout partner needs partner support for the token and network and external authorization first");
    }
}
