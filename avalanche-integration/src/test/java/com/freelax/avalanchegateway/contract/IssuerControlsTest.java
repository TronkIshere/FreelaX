package com.freelax.avalanchegateway.contract;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P3-C4: which issuer controls block an open escrow, and which paths they block (O-6: reported, never bypassed). */
class IssuerControlsTest {
    private static final String CLIENT = "0x" + "c1".repeat(20);
    private static final String FREELANCER = "0x" + "f1".repeat(20);
    private static final String ESCROW = "0x" + "e5".repeat(20);

    private static EscrowState escrow(int status) {
        return new EscrowState(CLIENT, FREELANCER, "0x" + "a1".repeat(20), BigInteger.valueOf(5_000_000), status, 2, 0, false,
                259_200, 0, 0, 0, 0, 0, 0, "0x" + "00".repeat(32), "0x" + "00".repeat(32), "0x" + "00".repeat(20), 0);
    }

    private static List<String> codes(List<IssuerControls.Alert> alerts) {
        return alerts.stream().map(IssuerControls.Alert::code).toList();
    }

    @Test
    void nothingIsReportedWhenNoControlApplies() {
        assertTrue(IssuerControls.assess(escrow(1), ESCROW, a -> false, () -> false).isEmpty());
    }

    @Test
    void eachControlIsReportedWithThePathsItBlocks() {
        Set<String> listed = Set.of(ESCROW, FREELANCER, CLIENT);
        List<IssuerControls.Alert> all = IssuerControls.assess(escrow(2), ESCROW, listed::contains, () -> true);
        assertEquals(List.of("TOKEN_PAUSED", "ESCROW_BLACKLISTED", "FREELANCER_BLACKLISTED", "CLIENT_BLACKLISTED"), codes(all));
        assertEquals(List.of("release", "resolve:release"), all.get(2).blocks(), "a blacklisted freelancer blocks the payout only");
        assertEquals(List.of("mutual-refund", "resolve:refund"), all.get(3).blocks(), "a blacklisted client blocks the refund only");
        assertEquals(FREELANCER, all.get(2).address());
    }

    @Test
    void onlyOpenEscrowsAreAssessed() {
        for (int status : new int[]{0, 5, 6}) {
            assertTrue(IssuerControls.assess(escrow(status), ESCROW, a -> true, () -> true).isEmpty(), "status " + status);
        }
        for (int status : new int[]{1, 2, 3, 4}) {
            assertEquals(4, IssuerControls.assess(escrow(status), ESCROW, a -> true, () -> true).size(), "status " + status);
        }
    }

    @Test
    void theNoteStatesThatNoRecoveryPathExists() {
        assertTrue(IssuerControls.NOTE.contains("no privileged withdrawal, bypass or rerouting"));
    }
}
