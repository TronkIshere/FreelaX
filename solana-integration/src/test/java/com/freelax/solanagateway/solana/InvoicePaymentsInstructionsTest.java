package com.freelax.solanagateway.solana;

import com.freelax.solanagateway.config.SolanaProperties;
import org.junit.jupiter.api.Test;
import org.p2p.solanaj.core.PublicKey;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePaymentsInstructionsTest {
    private final InvoicePaymentsInstructions instructions = new InvoicePaymentsInstructions(new SolanaAddresses(
            new SolanaProperties("http://127.0.0.1:9123", "2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb",
                    "confirmed", "", "test", true, 120, "")));
    private final PublicKey client = new PublicKey("CoW1R78Mdr3uBPiowUUTogJVguNLF4PHP1nCkkiBD53g");
    private final PublicKey freelancer = new PublicKey("7t5gAptbUsGNX8A3F7Ho3qtVbiToFa3f7UfmdWnNx11X");
    private final PublicKey admin = new PublicKey("AiNiVkfTY33inML6ZEBuPp7ymgjkzpsQe1GPfvDdBfCY");
    private final PublicKey mint = new PublicKey("DXeZia7qViiE8nsF4XLz2NH1yeZF57Wk2kbCn3JYExRZ");

    @Test
    void fundEncodesTheHighValueReviewGraceFlagAsTheLastArgument() {
        byte[] unified = data(false), legacy = data(true);
        // discriminator 8 + milestone 16 + freelancer 32 + amount 8 + funding 8 + delivery 8 + review 2 + revisions 1 + grace 1
        assertThat(unified).hasSize(84);
        assertThat(unified[83]).isEqualTo((byte) 0);
        assertThat(legacy[83]).isEqualTo((byte) 1);
        assertThat(unified[82]).isEqualTo((byte) 2);
        assertThat(java.util.Arrays.copyOf(unified, 83)).isEqualTo(java.util.Arrays.copyOf(legacy, 83));
    }

    private byte[] data(boolean grace) {
        return instructions.fundMilestoneEscrow(client, admin, freelancer, mint,
                "36fe6b2b-f9db-49c0-a1bc-47bafb5c743a", "501000000", 1_791_700_000L, 1_791_800_000L,
                72, 2, grace).getData();
    }
}
