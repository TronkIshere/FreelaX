package com.freelax.solanagateway.solana;

import com.freelax.solanagateway.config.SolanaProperties;
import org.p2p.solanaj.core.PublicKey;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class SolanaAddresses {

    public static final PublicKey SYSTEM_PROGRAM = new PublicKey("11111111111111111111111111111111");
    public static final PublicKey TOKEN_PROGRAM = new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");
    public static final PublicKey ASSOCIATED_TOKEN_PROGRAM = new PublicKey("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL");
    public static final PublicKey UPGRADEABLE_LOADER = new PublicKey("BPFLoaderUpgradeab1e11111111111111111111111");

    private final PublicKey programId;

    public SolanaAddresses(SolanaProperties properties) {
        this.programId = SolanaValueCodec.publicKey(properties.programId());
    }

    public PublicKey programId() {
        return programId;
    }

    public PublicKey config() {
        return pda(List.of(seed("config")), programId);
    }

    public PublicKey invoice(PublicKey freelancer, String invoiceId) {
        return pda(List.of(seed("invoice"), freelancer.toByteArray(),
                SolanaValueCodec.u64Le(invoiceId, "invoiceId")), programId);
    }

    public PublicKey rate(String rateId) {
        return pda(List.of(seed("rate"), SolanaValueCodec.u64Le(rateId, "rateId")), programId);
    }

    public PublicKey withdrawal(PublicKey freelancer, String withdrawalId) {
        return pda(List.of(seed("withdrawal"), freelancer.toByteArray(),
                SolanaValueCodec.u64Le(withdrawalId, "withdrawalId")), programId);
    }

    public PublicKey mockOnrampReceipt(PublicKey client, String purchaseId) {
        return pda(List.of(seed("mock_onramp"), client.toByteArray(),
                SolanaValueCodec.u64Le(purchaseId, "purchaseId")), programId);
    }

    public PublicKey mockOnrampTreasuryAuthority() {
        return pda(List.of(seed("mock_onramp_treasury")), programId);
    }

    public PublicKey programData() {
        return pda(List.of(programId.toByteArray()), UPGRADEABLE_LOADER);
    }

    public PublicKey ata(PublicKey owner, PublicKey mint) {
        return pda(List.of(owner.toByteArray(), TOKEN_PROGRAM.toByteArray(), mint.toByteArray()),
                ASSOCIATED_TOKEN_PROGRAM);
    }

    private PublicKey pda(List<byte[]> seeds, PublicKey owner) {
        return PublicKey.findProgramAddress(seeds, owner).getAddress();
    }

    private byte[] seed(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
