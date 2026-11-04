package com.marketplace.backend.payout.avalanche;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V8 on a real MySQL (opt-in, runs only with {@code -Dfreelax.mysql.url}; throwaway local MySQL only). Flyway applies V1 to V8,
 * Hibernate validates every entity, then the uniqueness rules the payout adapter depends on: one payout per milestone, one
 * payout per transfer hash, one row per partner order.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "freelax.mysql.url", matches = ".+")
class AvalancheV8MySqlIT {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("freelax.mysql.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("freelax.mysql.user", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("freelax.mysql.password"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired AvalanchePayoutRequestRepository payouts;

    private static AvalanchePayoutRequest row(UUID milestone, String partnerOrder, String txHash) {
        AvalanchePayoutRequest r = new AvalanchePayoutRequest();
        r.setId(UUID.randomUUID());
        r.setContractId(UUID.randomUUID());
        r.setMilestoneId(milestone);
        r.setFreelancerUserId(UUID.randomUUID());
        r.setNetworkId("avalanche-local");
        r.setEnvironment("LOCAL_SIMULATION");
        r.setChainId(31337);
        r.setTokenAddress("0x" + "aa".repeat(20));
        r.setEscrowId("0x" + "11".repeat(32));
        r.setAmountBaseUnits("5000000");
        r.setFromAddress("0x" + "f1".repeat(20));
        r.setPartnerId("local-mock");
        r.setPartnerOrderId(partnerOrder);
        r.setDepositAddress("0x" + "de".repeat(20));
        r.setPartnerSimulated(true);
        r.setStatus(txHash == null ? AvalanchePayoutStatus.AWAITING_USER_TRANSFER : AvalanchePayoutStatus.TRANSFER_SUBMITTED);
        r.setTransferTxHash(txHash);
        r.setCreatedAt(Instant.now());
        r.setUpdatedAt(Instant.now());
        return r;
    }

    @Test
    void v8RoundTripsAndEnforcesItsUniqueness() {
        UUID milestone = UUID.randomUUID();
        String tx = "0x" + UUID.randomUUID().toString().replace("-", "").repeat(2);
        AvalanchePayoutRequest saved = payouts.saveAndFlush(row(milestone, "SIMULATED-" + UUID.randomUUID(), tx));
        AvalanchePayoutRequest read = payouts.findById(saved.getId()).orElseThrow();
        assertThat(read.getStatus()).isEqualTo(AvalanchePayoutStatus.TRANSFER_SUBMITTED);
        assertThat(read.isPartnerSimulated()).isTrue();
        assertThat(read.getPartnerReportedFee()).isNull();
        assertThat(payouts.findByNetworkIdAndTransferTxHash("avalanche-local", tx)).isPresent();

        assertThatThrownBy(() -> payouts.saveAndFlush(row(milestone, "SIMULATED-" + UUID.randomUUID(), null)))
                .as("one payout per milestone").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payouts.saveAndFlush(row(UUID.randomUUID(), "SIMULATED-" + UUID.randomUUID(), tx)))
                .as("one payout per transfer").isInstanceOf(DataIntegrityViolationException.class);
        String order = "SIMULATED-" + UUID.randomUUID();
        payouts.saveAndFlush(row(UUID.randomUUID(), order, null));
        assertThatThrownBy(() -> payouts.saveAndFlush(row(UUID.randomUUID(), order, null)))
                .as("one row per partner order").isInstanceOf(DataIntegrityViolationException.class);
        // two rows still waiting for a transfer (NULL hash) do not collide
        payouts.saveAndFlush(row(UUID.randomUUID(), "SIMULATED-" + UUID.randomUUID(), null));
    }
}
