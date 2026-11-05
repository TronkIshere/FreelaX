package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.AvalancheClient;
import com.marketplace.backend.dto.response.avalanche.EvmBoundWalletResponse;
import com.marketplace.backend.dto.response.avalanche.EvmWalletChallengeResponse;
import com.marketplace.backend.entity.EvmWallet;
import com.marketplace.backend.entity.WalletLinkChallenge;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.EvmWalletRepository;
import com.marketplace.backend.repository.WalletLinkChallengeRepository;
import com.marketplace.backend.service.EvmWalletLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class EvmWalletLinkServiceImpl implements EvmWalletLinkService {
    public static final String MESSAGE_HEADER = "FreelaX EVM wallet binding\n";
    static final long CHALLENGE_TTL_SECONDS = 300;
    private static final Pattern EVM_ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final Pattern SIGNATURE = Pattern.compile("0x[0-9a-fA-F]{130}");

    private final EvmWalletRepository evmWallets;
    private final WalletLinkChallengeRepository challenges;
    private final AvalancheClient gateway;
    private final Clock clock;

    @Autowired
    public EvmWalletLinkServiceImpl(EvmWalletRepository evmWallets, WalletLinkChallengeRepository challenges, AvalancheClient gateway) {
        this(evmWallets, challenges, gateway, Clock.systemUTC());
    }

    EvmWalletLinkServiceImpl(EvmWalletRepository evmWallets, WalletLinkChallengeRepository challenges, AvalancheClient gateway, Clock clock) {
        this.evmWallets = evmWallets;
        this.challenges = challenges;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public EvmBoundWalletResponse current(UUID userId) {
        return evmWallets.findByUserId(userId).map(w -> new EvmBoundWalletResponse(w.getAddress())).orElse(null);
    }

    @Override
    @Transactional
    public EvmWalletChallengeResponse challenge(UUID userId, String walletAddress) {
        String address = normalized(walletAddress);
        EvmWallet existing = evmWallets.findByUserId(userId).orElse(null);
        boolean otherAddressLinked = existing != null && !existing.getAddress().equals(address);
        boolean addressTakenByAnotherUser = existing == null && evmWallets.existsByAddress(address);
        if (otherAddressLinked || addressTakenByAnotherUser) {
            throw invalid();
        }
        Instant expires = clock.instant().plusSeconds(CHALLENGE_TTL_SECONDS);
        WalletLinkChallenge row = new WalletLinkChallenge();
        row.setUserId(userId);
        row.setWalletAddress(address);
        row.setExpiresAt(expires);
        row.setMessage(MESSAGE_HEADER + "User: " + userId + "\nWallet: " + address + "\nNonce: " + UUID.randomUUID()
                + "\nExpires: " + expires + "\nDemonstration only: no real funds.");
        challenges.saveAndFlush(row);
        return new EvmWalletChallengeResponse(row.getId(), address, row.getMessage(), expires);
    }

    /** Commits on a rejection so that a consumed challenge stays consumed; a gateway outage still rolls back (retryable). */
    @Override
    @Transactional(noRollbackFor = ApplicationException.class)
    public EvmBoundWalletResponse verify(UUID userId, UUID challengeId, String signature) {
        if (challengeId == null || signature == null || !SIGNATURE.matcher(signature).matches()) {
            throw invalid();
        }
        WalletLinkChallenge row = challenges.findWithLockById(challengeId).orElseThrow(this::invalid);
        if (!isUsableBy(row, userId)) {
            throw invalid();
        }
        // Consumed before the outcome is known: a challenge is single-use even when the signature turns out invalid.
        row.setUsedAt(clock.instant());
        challenges.saveAndFlush(row);
        if (!gateway.verifyWalletSignature(row.getWalletAddress(), row.getMessage(), signature)) {
            throw invalid();
        }
        return bind(userId, row.getWalletAddress());
    }

    private boolean isUsableBy(WalletLinkChallenge row, UUID userId) {
        boolean ownedAndFresh = userId.equals(row.getUserId()) && row.getUsedAt() == null && clock.instant().isBefore(row.getExpiresAt());
        boolean wellFormed = row.getWalletAddress() != null && EVM_ADDRESS.matcher(row.getWalletAddress()).matches()
                && row.getMessage() != null && row.getMessage().startsWith(MESSAGE_HEADER);
        return ownedAndFresh && wellFormed;
    }

    private EvmBoundWalletResponse bind(UUID userId, String address) {
        EvmWallet existing = evmWallets.findByUserId(userId).orElse(null);
        if (existing != null) {
            if (!existing.getAddress().equals(address)) {
                throw invalid();
            }
            return new EvmBoundWalletResponse(existing.getAddress());
        }
        if (evmWallets.existsByAddress(address)) {
            throw invalid();
        }
        EvmWallet wallet = new EvmWallet();
        wallet.setUserId(userId);
        wallet.setAddress(address);
        wallet.setLinkedAt(clock.instant());
        evmWallets.saveAndFlush(wallet);
        return new EvmBoundWalletResponse(wallet.getAddress());
    }

    private String normalized(String address) {
        if (address == null || !EVM_ADDRESS.matcher(address).matches()) {
            throw invalid();
        }
        return address.toLowerCase(Locale.ROOT);
    }

    private ApplicationException invalid() {
        return new ApplicationException(ErrorCode.INVALID_DATA, "the EVM wallet could not be linked");
    }
}
