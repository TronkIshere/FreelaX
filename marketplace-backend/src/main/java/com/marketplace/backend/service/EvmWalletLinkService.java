package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.avalanche.EvmBoundWalletResponse;
import com.marketplace.backend.dto.response.avalanche.EvmWalletChallengeResponse;

import java.util.UUID;

/**
 * Binds an EVM address to a user for the Avalanche rail (register A8). The user's own wallet signs a single-use challenge
 * with EIP-191 {@code personal_sign}; the Avalanche gateway recovers the signer. The marketplace never sees a key. Solana
 * wallets ({@code wallets} table, {@link WalletLinkService}) are untouched: EVM addresses live in {@code evm_wallets}.
 * One EVM address per user and one user per address; relinking a different address is refused.
 */
public interface EvmWalletLinkService {

    /** The wallet bound to the user, or null when none is linked. */
    EvmBoundWalletResponse current(UUID userId);

    EvmWalletChallengeResponse challenge(UUID userId, String walletAddress);

    EvmBoundWalletResponse verify(UUID userId, UUID challengeId, String signature);
}
