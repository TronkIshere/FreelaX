package com.marketplace.backend.entity;

import com.marketplace.backend.entity.AvalanchePayoutRequest;
import com.marketplace.backend.entity.AvalanchePayoutStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;

import static com.marketplace.backend.entity.AvalanchePayoutStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvalanchePayoutStatusTest {

    @Test
    void onlyTheDocumentedTransitionsExist() {
        int allowed = 0;
        for (AvalanchePayoutStatus from : values()) {
            for (AvalanchePayoutStatus to : values()) {
                if (from.canMoveTo(to)) allowed++;
            }
        }
        assertEquals(9, allowed, "AWAITING->SUBMITTED; SUBMITTED->CONFIRMED|FAILED; FAILED->SUBMITTED; CONFIRMED->3; PENDING->2");
        assertTrue(AWAITING_USER_TRANSFER.canMoveTo(TRANSFER_SUBMITTED));
        assertTrue(TRANSFER_FAILED.canMoveTo(TRANSFER_SUBMITTED), "a new transfer may follow a definitive on-chain failure");
        assertFalse(AWAITING_USER_TRANSFER.canMoveTo(TRANSFER_CONFIRMED), "no confirmation without a recorded transfer");
        assertFalse(TRANSFER_SUBMITTED.canMoveTo(FIAT_PAYOUT_CONFIRMED), "fiat payout never skips the on-chain confirmation");
        assertFalse(TRANSFER_FAILED.canMoveTo(FIAT_PAYOUT_PENDING));
    }

    @Test
    void terminalStatesAreFinal() {
        assertEquals(EnumSet.of(FIAT_PAYOUT_CONFIRMED, FIAT_PAYOUT_FAILED),
                EnumSet.copyOf(Arrays.stream(values()).filter(AvalanchePayoutStatus::terminal).toList()));
        AvalanchePayoutRequest r = new AvalanchePayoutRequest();
        r.setStatus(FIAT_PAYOUT_CONFIRMED);
        assertThrows(IllegalStateException.class, () -> r.moveTo(TRANSFER_SUBMITTED));
        assertEquals(FIAT_PAYOUT_CONFIRMED, r.getStatus(), "an illegal move leaves the status unchanged");
    }

    @Test
    void onChainConfirmationIsNotFiatConfirmation() {
        assertTrue(TRANSFER_CONFIRMED.onChainTransferConfirmed());
        assertTrue(FIAT_PAYOUT_FAILED.onChainTransferConfirmed(), "a failed fiat payout does not undo the on-chain transfer");
        assertFalse(TRANSFER_SUBMITTED.onChainTransferConfirmed());
        assertFalse(TRANSFER_FAILED.onChainTransferConfirmed());
    }
}
