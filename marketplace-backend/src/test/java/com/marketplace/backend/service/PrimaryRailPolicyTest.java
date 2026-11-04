package com.marketplace.backend.service;

import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.exception.ApplicationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phase 3 P3-C2: primary rail for NEW contracts; default unchanged; fail closed. */
class PrimaryRailPolicyTest {

    private static AvalancheGatewayProperties gateway(boolean enabled) {
        AvalancheGatewayProperties p = new AvalancheGatewayProperties();
        p.setEnabled(enabled);
        p.setNetwork("avalanche-local");
        return p;
    }

    @Test
    void solanaIsTheDefaultAndChangesNothing() {
        PrimaryRailPolicy policy = new PrimaryRailPolicy("SOLANA", gateway(true));
        WorkContract c = new WorkContract();
        policy.applyToNewContract(c);
        assertThat(c.getPaymentNetworkId()).isNull();
        assertThat(policy.rail()).isEqualTo(PrimaryRailPolicy.Rail.SOLANA);
    }

    @Test
    void avalancheRecordsTheConfiguredNetworkOnTheNewContract() {
        WorkContract c = new WorkContract();
        new PrimaryRailPolicy(" avalanche ", gateway(true)).applyToNewContract(c);
        assertThat(c.getPaymentNetworkId()).isEqualTo("avalanche-local");
    }

    @Test
    void avalancheWithTheGatewayDisabledRefusesInsteadOfFallingBack() {
        WorkContract c = new WorkContract();
        assertThatThrownBy(() -> new PrimaryRailPolicy("AVALANCHE", gateway(false)).applyToNewContract(c))
                .isInstanceOf(ApplicationException.class);
        assertThat(c.getPaymentNetworkId()).isNull();
    }

    @Test
    void anUnknownRailStopsStartup() {
        assertThatThrownBy(() -> new PrimaryRailPolicy("MAINNET", gateway(true))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PrimaryRailPolicy("", gateway(true))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PrimaryRailPolicy(null, gateway(true))).isInstanceOf(IllegalStateException.class);
    }
}
