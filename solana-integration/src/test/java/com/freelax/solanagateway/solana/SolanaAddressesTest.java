package com.freelax.solanagateway.solana;

import com.freelax.solanagateway.config.SolanaProperties;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;

import static org.assertj.core.api.Assertions.assertThat;

class SolanaAddressesTest {

    private final SolanaAddresses addresses = new SolanaAddresses(new SolanaProperties(
            "http://127.0.0.1:9123", "CwuaAPrxYLK6avPUbMRBerBYt1apdNU829TDZmoAnhEf",
            "confirmed", "", "test", true, 120, ""));

    @Test
    void derivesCanonicalProgramAddresses() {
        PublicKey zero = new PublicKey("11111111111111111111111111111111");

        assertThat(addresses.config().toBase58())
                .isEqualTo("8MREDeC7ikBDku6jZAzPaEVa3fhePzyUykxfLSQ5gqBj");
        assertThat(addresses.invoice(zero, "42").toBase58())
                .isEqualTo("7MM3c6BVuxqWRko677jh5d66uAGNeNQNanVMHbWgCYCG");
        assertThat(addresses.rate("42").toBase58())
                .isEqualTo("J1u6ZKojUQKVrebYvquUt411JkVzmhQfFmPx4ir2KFYY");
        assertThat(addresses.withdrawal(zero, "42").toBase58())
                .isEqualTo("H2v9uC4f7Y7h3TKbVzFW5sSye5P29yHsMKXjRcN1eudM");
        assertThat(addresses.mockOnrampTreasuryAuthority().toBase58())
                .isEqualTo("4e8uYMYreThjN5qdjUPCmpsGkDeSxeCCkCTLqQJxXq4e");
    }
}
