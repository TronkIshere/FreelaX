package com.marketplace.backend.service;

import com.marketplace.backend.entity.AvalanchePendingAuthorization;
import com.marketplace.backend.entity.EvmWallet;
import com.marketplace.backend.repository.AvalanchePendingAuthorizationRepository;
import com.marketplace.backend.repository.EvmWalletRepository;
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
 * V7 on a real MySQL (opt-in, like {@code PaymentFlowMySqlConcurrencyIT}: runs only with {@code -Dfreelax.mysql.url}).
 * Flyway applies V1 to V7 and Hibernate validates every entity against the resulting schema; then the uniqueness rules that
 * A8 (one EVM address per user, one user per address) and F13 (one pending pair per contract and action) depend on.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfSystemProperty(named = "freelax.mysql.url", matches = ".+")
class AvalancheV7MySqlIT {
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

    @Autowired EvmWalletRepository evmWallets;
    @Autowired AvalanchePendingAuthorizationRepository pending;

    private static EvmWallet wallet(UUID user, String address) {
        EvmWallet w = new EvmWallet();
        w.setUserId(user);
        w.setAddress(address);
        w.setLinkedAt(Instant.now());
        return w;
    }

    @Test
    void oneEvmAddressPerUserAndOneUserPerAddress() {
        UUID user = UUID.randomUUID();
        String address = "0x" + UUID.randomUUID().toString().replace("-", "").substring(0, 32) + "00000000";
        evmWallets.saveAndFlush(wallet(user, address));
        assertThat(evmWallets.findByUserId(user)).map(EvmWallet::getAddress).contains(address);
        assertThat(evmWallets.existsByAddress(address)).isTrue();
        assertThatThrownBy(() -> evmWallets.saveAndFlush(wallet(user, "0x" + "ab".repeat(20))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> evmWallets.saveAndFlush(wallet(UUID.randomUUID(), address)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onePendingAuthorizationPerContractAndAction() {
        UUID contract = UUID.randomUUID();
        AvalanchePendingAuthorization a = new AvalanchePendingAuthorization();
        a.setContractId(contract);
        a.setAction("mutual-refund");
        a.setAuthorizationDeadline(1_900_000_000L);
        a.setTypedDataDigest("0x" + "d1".repeat(32));
        a.setClientSignature("0x" + "c1".repeat(65));
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        pending.saveAndFlush(a);
        AvalanchePendingAuthorization read = pending.findByContractIdAndAction(contract, "mutual-refund").orElseThrow();
        assertThat(read.getClientSignature()).hasSize(132);
        assertThat(read.getFreelancerSignature()).isNull();

        AvalanchePendingAuthorization duplicate = new AvalanchePendingAuthorization();
        duplicate.setContractId(contract);
        duplicate.setAction("mutual-refund");
        duplicate.setAuthorizationDeadline(1_900_000_100L);
        duplicate.setTypedDataDigest("0x" + "d2".repeat(32));
        duplicate.setCreatedAt(Instant.now());
        duplicate.setUpdatedAt(Instant.now());
        assertThatThrownBy(() -> pending.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
