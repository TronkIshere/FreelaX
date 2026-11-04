package com.marketplace.backend.service;

import com.marketplace.backend.client.AvalancheClient;
import com.marketplace.backend.exception.AvalancheGatewayException;
import com.marketplace.backend.entity.EvmWallet;
import com.marketplace.backend.entity.WalletLinkChallenge;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.repository.EvmWalletRepository;
import com.marketplace.backend.repository.WalletLinkChallengeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Register item A8: EVM wallet linking: authorization, single use, expiry, format, uniqueness, and gateway outcomes. */
class EvmWalletLinkServiceTest {
    private static final String ADDRESS = "0xAbCdEf0123456789abcdef0123456789ABCDEF01";
    private static final String LOWER = ADDRESS.toLowerCase();
    private static final String SIG = "0x" + "1b".repeat(65);

    private final EvmWalletRepository evmWallets = mock(EvmWalletRepository.class);
    private final WalletLinkChallengeRepository challenges = mock(WalletLinkChallengeRepository.class);
    private final AvalancheClient gateway = mock(AvalancheClient.class);
    private final Map<UUID, WalletLinkChallenge> rows = new HashMap<>();
    private Instant now = Instant.parse("2026-11-04T10:00:00Z");
    private EvmWalletLinkService service;
    private final UUID user = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EvmWalletLinkService(evmWallets, challenges, gateway, new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        });
        when(challenges.saveAndFlush(any(WalletLinkChallenge.class))).thenAnswer(i -> {
            WalletLinkChallenge c = i.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            rows.put(c.getId(), c);
            return c;
        });
        when(challenges.findWithLockById(any())).thenAnswer(i -> Optional.ofNullable(rows.get((UUID) i.getArgument(0))));
        when(evmWallets.findByUserId(any())).thenReturn(Optional.empty());
        when(evmWallets.saveAndFlush(any(EvmWallet.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void theChallengeIsAnExplicitSingleUseBindingMessageForTheNormalisedAddress() {
        EvmWalletLinkService.Challenge c = service.challenge(user, ADDRESS);
        assertEquals(LOWER, c.walletAddress());
        assertTrue(c.message().startsWith(EvmWalletLinkService.MESSAGE_HEADER));
        assertTrue(c.message().contains("User: " + user) && c.message().contains("Wallet: " + LOWER));
        assertTrue(c.message().contains("Demonstration only"));
        assertEquals(now.plusSeconds(300), c.expiresAt());
        assertTrue(c.message().length() <= 400, "fits the challenge column");
    }

    @Test
    void malformedSolanaOrTakenAddressesAreRefused() {
        assertThrows(ApplicationException.class, () -> service.challenge(user, "0x1234"));
        assertThrows(ApplicationException.class, () -> service.challenge(user, "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV"));
        assertThrows(ApplicationException.class, () -> service.challenge(user, null));
        when(evmWallets.existsByAddress(LOWER)).thenReturn(true);
        assertThrows(ApplicationException.class, () -> service.challenge(user, ADDRESS), "another user holds this address");
    }

    @Test
    void aUserWithALinkedAddressCannotSwitchToAnother() {
        EvmWallet linked = new EvmWallet();
        linked.setUserId(user);
        linked.setAddress("0x" + "2".repeat(40));
        when(evmWallets.findByUserId(user)).thenReturn(Optional.of(linked));
        assertThrows(ApplicationException.class, () -> service.challenge(user, ADDRESS));
    }

    @Test
    void aValidSignatureLinksTheAddressOnceAndTheChallengeCannotBeReplayed() {
        EvmWalletLinkService.Challenge c = service.challenge(user, ADDRESS);
        when(gateway.verifyWalletSignature(LOWER, c.message(), SIG)).thenReturn(true);
        assertEquals(LOWER, service.verify(user, c.challengeId(), SIG).walletAddress());
        verify(evmWallets).saveAndFlush(any(EvmWallet.class));
        assertNotNull(rows.get(c.challengeId()).getUsedAt());
        assertThrows(ApplicationException.class, () -> service.verify(user, c.challengeId(), SIG), "replay of a used challenge");
    }

    @Test
    void anInvalidSignatureConsumesTheChallengeAndLinksNothing() throws Exception {
        EvmWalletLinkService.Challenge c = service.challenge(user, ADDRESS);
        when(gateway.verifyWalletSignature(anyString(), anyString(), anyString())).thenReturn(false);
        assertThrows(ApplicationException.class, () -> service.verify(user, c.challengeId(), SIG));
        assertNotNull(rows.get(c.challengeId()).getUsedAt(), "single use even when rejected");
        verify(evmWallets, never()).saveAndFlush(any());
        Transactional tx = EvmWalletLinkService.class.getMethod("verify", UUID.class, UUID.class, String.class).getAnnotation(Transactional.class);
        assertEquals(ApplicationException.class, tx.noRollbackFor()[0], "the consumed challenge is committed on rejection");
    }

    @Test
    void anotherUsersOrAnExpiredChallengeIsRefusedWithoutAskingTheGateway() {
        EvmWalletLinkService.Challenge c = service.challenge(user, ADDRESS);
        assertThrows(ApplicationException.class, () -> service.verify(UUID.randomUUID(), c.challengeId(), SIG));
        now = now.plusSeconds(301);
        assertThrows(ApplicationException.class, () -> service.verify(user, c.challengeId(), SIG));
        verify(gateway, never()).verifyWalletSignature(anyString(), anyString(), anyString());
    }

    @Test
    void aMalformedSignatureOrASolanaChallengeIsRefusedWithoutAskingTheGateway() {
        EvmWalletLinkService.Challenge c = service.challenge(user, ADDRESS);
        assertThrows(ApplicationException.class, () -> service.verify(user, c.challengeId(), "0x12"));
        assertThrows(ApplicationException.class, () -> service.verify(user, c.challengeId(), null));
        WalletLinkChallenge solana = new WalletLinkChallenge();
        solana.setId(UUID.randomUUID());
        solana.setUserId(user);
        solana.setWalletAddress("7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV");
        solana.setMessage("FreelaX wallet binding\nUser: " + user);
        solana.setExpiresAt(now.plusSeconds(60));
        rows.put(solana.getId(), solana);
        assertThrows(ApplicationException.class, () -> service.verify(user, solana.getId(), SIG));
        verify(gateway, never()).verifyWalletSignature(anyString(), anyString(), anyString());
    }

    @Test
    void aGatewayOutageLinksNothingAndPropagatesSoTheTransactionRollsBack() {
        EvmWalletLinkService.Challenge c = service.challenge(user, ADDRESS);
        when(gateway.verifyWalletSignature(anyString(), anyString(), anyString()))
                .thenThrow(new AvalancheGatewayException("down", false, null, null));
        assertThrows(AvalancheGatewayException.class, () -> service.verify(user, c.challengeId(), SIG));
        verify(evmWallets, never()).saveAndFlush(any());
    }

    @Test
    void anAddressTakenBetweenChallengeAndVerifyIsRefused() {
        EvmWalletLinkService.Challenge c = service.challenge(user, ADDRESS);
        when(gateway.verifyWalletSignature(anyString(), anyString(), anyString())).thenReturn(true);
        when(evmWallets.existsByAddress(LOWER)).thenReturn(true);
        assertThrows(ApplicationException.class, () -> service.verify(user, c.challengeId(), SIG));
        verify(evmWallets, never()).saveAndFlush(any());
    }
}
