package com.marketplace.backend.client;

import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.exception.SolanaCprException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R13 (owner decision 2026-11-12, option a): with the Solana rail disabled every gateway call fails closed, without a request. */
class SolanaCprClientDisabledTest {

    private static SolanaCprClient client(boolean enabled) {
        SolanaCprProperties p = new SolanaCprProperties();
        p.setEnabled(enabled);
        // an address that refuses connections: if a request were attempted the message would say CONNECTION_FAILED
        p.setBaseUrl("http://127.0.0.1:9");
        p.setConnectTimeoutMs(500);
        p.setReadTimeoutMs(500);
        return new SolanaCprClient(new RestTemplateBuilder(), p);
    }

    @Test
    void everyCallFailsClosedWithSolanaRailDisabledAndIsNotDefinitive() {
        SolanaCprClient c = client(false);
        List<Consumer<SolanaCprClient>> calls = List.of(
                x -> x.getConfig(),
                x -> x.getTransactionStatus("sig"),
                x -> x.findEscrow("m1"),
                x -> x.findRate("r1"),
                x -> x.findInvoice("f", "i"),
                x -> x.findWithdrawal("f", "w"),
                x -> x.findMockOnrampReceipt("c", "p"),
                x -> x.buildEscrowFund("m1", new Object()),
                x -> x.sendEscrowAction("m1", "release", new Object()),
                x -> x.submitEscrowSigned("s", "tx"),
                x -> x.publishRate(null),
                x -> x.requestOfframp(null));
        for (Consumer<SolanaCprClient> call : calls) {
            SolanaCprException e = assertThrows(SolanaCprException.class, () -> call.accept(c));
            assertTrue(e.getMessage().endsWith(": SOLANA_RAIL_DISABLED"), e.getMessage());
            assertFalse(e.isDefinitive(), "same retry semantics as an unreachable gateway");
            assertNull(e.getHttpStatus());
        }
    }

    @Test
    void whenEnabledTheClientStillTriesTheGateway() {
        SolanaCprException e = assertThrows(SolanaCprException.class, () -> client(true).getConfig());
        assertEquals("get-config: CONNECTION_FAILED", e.getMessage());
    }
}
