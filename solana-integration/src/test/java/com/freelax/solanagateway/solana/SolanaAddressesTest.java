package com.freelax.solanagateway.solana;

import com.freelax.solanagateway.config.SolanaProperties;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;

import static org.assertj.core.api.Assertions.assertThat;

class SolanaAddressesTest {

    private final SolanaAddresses addresses = new SolanaAddresses(new SolanaProperties(
            "http://127.0.0.1:9123", "2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb",
            "confirmed", "", "test", true, 120, ""));

    @Test
    void derivesCanonicalProgramAddresses() {
        PublicKey zero = new PublicKey("11111111111111111111111111111111");

        assertThat(addresses.config().toBase58())
                .isEqualTo("9dPw1tBnD85mVGyS41RRQCjjReBoZjkEt9qrYxdCoRvc");
        assertThat(addresses.invoice(zero, "42").toBase58())
                .isEqualTo("E7riCra953fdQHN5ZwznNMgWSWGyGWYc2evdogd1iVhS");
        assertThat(addresses.rate("42").toBase58())
                .isEqualTo("7tteMhQ7sau61k6KqEjXtYkkBVAhCi5YZZD12doTBEUR");
        assertThat(addresses.withdrawal(zero, "42").toBase58())
                .isEqualTo("HXx3opUBHanLP87UKrFcW3GBm1kusCJVZNvMFaDkreoD");
        assertThat(addresses.mockOnrampTreasuryAuthority().toBase58())
                .isEqualTo("7ajhzqfmefEBv8Aq4hxXn4Cqd4YP2UTNKMpXLS8zsCAq");
    }
}
