package com.freelax.solanagateway.solana;

import com.freelax.solanagateway.config.SolanaProperties;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;

import static org.assertj.core.api.Assertions.assertThat;

class SolanaAddressesTest {

    private final SolanaAddresses addresses = new SolanaAddresses(new SolanaProperties(
            "http://127.0.0.1:9123", "4Wd6umju26vej2ftzwR6J55pjkUqDQsxfVkt46UqDb1b",
            "confirmed", "", "test", true, 120, ""));

    @Test
    void derivesCanonicalProgramAddresses() {
        PublicKey zero = new PublicKey("11111111111111111111111111111111");

        assertThat(addresses.config().toBase58())
                .isEqualTo("EecUo2QqJjctz8EFvbiSxKF5rbU4AvPnVHbSiivUqUdb");
        assertThat(addresses.invoice(zero, "42").toBase58())
                .isEqualTo("6o1jYvTfhymLqmBy4CifV9N5SVQfptxbVj1PXya8Pxa5");
        assertThat(addresses.rate("42").toBase58())
                .isEqualTo("4sb336jxELzA75B94UrXJJtRzh2sPE1c4izHvynEnZcx");
        assertThat(addresses.withdrawal(zero, "42").toBase58())
                .isEqualTo("Gy3QESR2gWV9dCTQQ5zEpkuEsKSvvZxw3qe6QHCMKbEy");
        assertThat(addresses.mockOnrampTreasuryAuthority().toBase58())
                .isEqualTo("833bBZDEi9ZUx9SA6URqma9Z1srt2D6Po9hK8jjpHQPh");
    }
}
