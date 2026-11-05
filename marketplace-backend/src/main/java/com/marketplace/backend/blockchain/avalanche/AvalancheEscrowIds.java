package com.marketplace.backend.blockchain.avalanche;

import java.util.Locale;
import java.util.UUID;

public final class AvalancheEscrowIds {

    private AvalancheEscrowIds() {
    }

    /** True for a contract that chose the Avalanche rail; the legacy funding paths must refuse such a contract (no mixed rails). */
    public static boolean isAvalancheNetwork(String paymentNetworkId) {
        return paymentNetworkId != null && paymentNetworkId.trim().toLowerCase(Locale.ROOT).startsWith("avalanche");
    }

    public static String fromMilestone(UUID milestoneId) {
        return "0x" + milestoneId.toString().replace("-", "") + "0".repeat(32);
    }

    public static UUID toMilestone(String escrowId) {
        if (escrowId == null || !escrowId.matches("0x[0-9a-fA-F]{32}0{32}")) {
            throw new IllegalArgumentException("not a milestone escrow id");
        }
        String hex = escrowId.substring(2, 34);
        return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20));
    }
}
