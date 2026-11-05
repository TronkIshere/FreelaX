package com.marketplace.backend.blockchain.avalanche;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvalancheEscrowIdsTest {
    @Test
    void onlyAnAvalancheNetworkIdSelectsTheAvalancheRail() {
        assertTrue(AvalancheEscrowIds.isAvalancheNetwork("avalanche-local"));
        assertTrue(AvalancheEscrowIds.isAvalancheNetwork("avalanche-fuji"));
        assertTrue(AvalancheEscrowIds.isAvalancheNetwork("Avalanche-Fuji"));
        assertTrue(AvalancheEscrowIds.isAvalancheNetwork("  avalanche-local "));
    }

    @Test
    void missingBlankOrOtherNetworkIdsNeverSelectTheAvalancheRail() {
        assertFalse(AvalancheEscrowIds.isAvalancheNetwork(null));
        assertFalse(AvalancheEscrowIds.isAvalancheNetwork(""));
        assertFalse(AvalancheEscrowIds.isAvalancheNetwork("   "));
        assertFalse(AvalancheEscrowIds.isAvalancheNetwork("solana-devnet"));
        assertFalse(AvalancheEscrowIds.isAvalancheNetwork("not-avalanche"));
    }
}
