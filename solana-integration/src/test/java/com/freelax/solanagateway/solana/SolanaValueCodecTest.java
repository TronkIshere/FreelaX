package com.freelax.solanagateway.solana;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolanaValueCodecTest {

    @Test
    void encodesFullUnsignedLongRangeInLittleEndian() {
        assertThat(SolanaValueCodec.u64Le(SolanaValueCodec.U64_MAX))
                .containsExactly(-1, -1, -1, -1, -1, -1, -1, -1);
        assertThat(SolanaValueCodec.u64Le(new BigInteger("42")))
                .containsExactly(42, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void rejectsOutOfRangeUnsignedLong() {
        assertThatThrownBy(() -> SolanaValueCodec.u64("-1", "amount"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolanaValueCodec.u64("18446744073709551616", "amount"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsAnchorInstructionDiscriminator() {
        assertThat(SolanaValueCodec.discriminator("global", "pay_invoice"))
                .containsExactly(104, 6, 62, -17, -59, -50, -48, -36);
    }
}
