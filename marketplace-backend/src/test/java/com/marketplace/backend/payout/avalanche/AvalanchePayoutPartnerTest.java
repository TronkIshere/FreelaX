package com.marketplace.backend.payout.avalanche;

import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvalanchePayoutPartnerTest {
    private static final String TOKEN = "0x" + "aa".repeat(20);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC);

    private static AvalancheGatewayProperties gateway(String network) {
        AvalancheGatewayProperties p = new AvalancheGatewayProperties();
        p.setNetwork(network);
        return p;
    }

    @Test
    void defaultsSelectTheDisabledPartnerWhichSupportsNothing() {
        AvalanchePayoutProperties defaults = new AvalanchePayoutProperties();
        assertFalse(defaults.isEnabled(), "disabled by default");
        AvalanchePayoutPartner p = AvalanchePayoutConfiguration.select(defaults, gateway("avalanche-local"), CLOCK);
        assertInstanceOf(DisabledAvalanchePayoutPartner.class, p);
        AvalanchePayoutPartner.Capability c = p.capability(31337, TOKEN);
        assertFalse(c.supported());
        assertEquals("PARTNER_NOT_CONFIGURED", c.reason());
        assertThrows(IllegalStateException.class, () -> p.createOrder(new AvalanchePayoutPartner.OrderRequest("r", 31337, TOKEN, "1", TOKEN)));
    }

    @Test
    void unknownPartnersAndTheMockOutsideTheLocalNetworkFailClosedAtStartup() {
        AvalanchePayoutProperties real = new AvalanchePayoutProperties();
        real.setPartner("some-real-offramp");
        assertThrows(IllegalStateException.class, () -> AvalanchePayoutConfiguration.select(real, gateway("avalanche-local"), CLOCK));
        AvalanchePayoutProperties mock = new AvalanchePayoutProperties();
        mock.setPartner("local-mock");
        mock.setLocalMockTokenAddress(TOKEN);
        assertThrows(IllegalStateException.class, () -> AvalanchePayoutConfiguration.select(mock, gateway("avalanche-fuji"), CLOCK),
                "the labelled mock never runs on the Fuji network id");
        assertInstanceOf(LocalMockAvalanchePayoutPartner.class, AvalanchePayoutConfiguration.select(mock, gateway("avalanche-local"), CLOCK));
    }

    @Test
    void theMockSupportsOnlyItsExplicitTokenOnTheLocalChainAndQuotesNothing() {
        LocalMockAvalanchePayoutPartner p = new LocalMockAvalanchePayoutPartner(TOKEN, "0x" + "de".repeat(20), CLOCK);
        assertTrue(p.simulated());
        assertTrue(p.capability(31337, TOKEN.toUpperCase().replace("0X", "0x")).supported());
        assertEquals("PARTNER_NETWORK_UNSUPPORTED", p.capability(43113, TOKEN).reason(), "Fuji is not supported by the mock");
        assertEquals("PARTNER_NETWORK_UNSUPPORTED", p.capability(43114, TOKEN).reason());
        assertEquals("PARTNER_TOKEN_UNSUPPORTED", p.capability(31337, "0x" + "bb".repeat(20)).reason(), "only the configured token");
        assertEquals("PARTNER_TOKEN_NOT_CONFIGURED",
                new LocalMockAvalanchePayoutPartner("", "0x" + "de".repeat(20), CLOCK).capability(31337, TOKEN).reason(),
                "no explicit token: no support");

        AvalanchePayoutPartner.Order o = p.createOrder(new AvalanchePayoutPartner.OrderRequest("ref-1", 31337, TOKEN, "5000000", "0x" + "f1".repeat(20)));
        assertTrue(o.partnerOrderId().startsWith("SIMULATED-"));
        assertEquals("0x" + "de".repeat(20), o.depositAddress());
        assertNull(o.partnerReportedFee(), "the mock invents no fee");
        assertNull(o.partnerReportedRate(), "the mock invents no rate");
        assertEquals(o.partnerOrderId(), p.createOrder(new AvalanchePayoutPartner.OrderRequest("ref-1", 31337, TOKEN, "5000000", "x")).partnerOrderId(),
                "idempotent per reference");
        assertThrows(IllegalArgumentException.class,
                () -> p.createOrder(new AvalanchePayoutPartner.OrderRequest("ref-2", 43113, TOKEN, "1", "0x" + "f1".repeat(20))));
        AvalanchePayoutPartner.FiatStatus f = p.fiatStatus(o.partnerOrderId(), "0x" + "01".repeat(32));
        assertEquals(AvalanchePayoutPartner.FiatState.PAID, f.state());
        assertTrue(f.reference().startsWith("SIMULATED-PAYOUT-"));
    }
}
