package com.freelax.solanagateway.solana;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InvoicePaymentsInstructions {

    private final SolanaAddresses addresses;

    public InvoicePaymentsInstructions(SolanaAddresses addresses) {
        this.addresses = addresses;
    }

    public TransactionInstruction initializeConfig(PublicKey admin, PublicKey mint,
                                                    PublicKey treasury, PublicKey rate,
                                                    PublicKey oracle, long maxRateAge) {
        return instruction("initialize_config", List.of(
                writableSigner(admin), writable(addresses.config()), readonly(mint),
                readonly(addresses.programId()), readonly(addresses.programData()),
                readonly(SolanaAddresses.SYSTEM_PROGRAM)
        ), treasury.toByteArray(), rate.toByteArray(), oracle.toByteArray(),
                SolanaValueCodec.i64Le(maxRateAge));
    }

    public TransactionInstruction updateConfig(PublicKey admin, PublicKey mint,
                                                PublicKey treasury, PublicKey rate,
                                                PublicKey oracle, long maxRateAge, boolean paused) {
        return instruction("update_config", List.of(
                signer(admin), writable(addresses.config()), readonly(mint)
        ), treasury.toByteArray(), rate.toByteArray(), oracle.toByteArray(),
                SolanaValueCodec.i64Le(maxRateAge), bool(paused));
    }

    public TransactionInstruction configureMockOnramp(PublicKey admin, PublicKey authority,
                                                       String maxAmount, boolean enabled) {
        return instruction("configure_mock_onramp", List.of(
                signer(admin), writable(addresses.config())
        ), authority.toByteArray(), SolanaValueCodec.u64Le(maxAmount, "maxAmount"), bool(enabled));
    }

    public TransactionInstruction mockOnramp(PublicKey authority, PublicKey mint, PublicKey client,
                                              String purchaseId, String usdAmount) {
        PublicKey treasuryAuthority = addresses.mockOnrampTreasuryAuthority();
        PublicKey treasuryAta = addresses.ata(treasuryAuthority, mint);
        PublicKey clientAta = addresses.ata(client, mint);
        PublicKey receipt = addresses.mockOnrampReceipt(client, purchaseId);
        return instruction("mock_onramp", List.of(
                writableSigner(authority), readonly(addresses.config()), readonly(mint),
                readonly(treasuryAuthority), writable(treasuryAta), readonly(client),
                writable(clientAta), writable(receipt), readonly(SolanaAddresses.ASSOCIATED_TOKEN_PROGRAM),
                readonly(SolanaAddresses.TOKEN_PROGRAM), readonly(SolanaAddresses.SYSTEM_PROGRAM)
        ), SolanaValueCodec.u64Le(purchaseId, "purchaseId"),
                SolanaValueCodec.u64Le(usdAmount, "usdAmountE6"));
    }

    public TransactionInstruction publishRate(PublicKey authority, String rateId, String usdcUsd,
                                               String usdVnd, long observedAt, long expiresAt,
                                               byte[] sourceHash) {
        return instruction("publish_rate", List.of(
                writableSigner(authority), readonly(addresses.config()), writable(addresses.rate(rateId)),
                readonly(SolanaAddresses.SYSTEM_PROGRAM)
        ), SolanaValueCodec.u64Le(rateId, "rateId"),
                SolanaValueCodec.u64Le(usdcUsd, "usdcUsdE6"),
                SolanaValueCodec.u64Le(usdVnd, "usdVndE6"),
                SolanaValueCodec.i64Le(observedAt), SolanaValueCodec.i64Le(expiresAt), sourceHash);
    }

    public TransactionInstruction createInvoice(PublicKey freelancer, PublicKey client,
                                                 String invoiceId, String amount, String rateId,
                                                 long expiresAt) {
        return instruction("create_invoice", List.of(
                writableSigner(freelancer), readonly(addresses.config()), readonly(addresses.rate(rateId)),
                writable(addresses.invoice(freelancer, invoiceId)), readonly(SolanaAddresses.SYSTEM_PROGRAM)
        ), SolanaValueCodec.u64Le(invoiceId, "invoiceId"), client.toByteArray(),
                SolanaValueCodec.u64Le(amount, "amount"), SolanaValueCodec.i64Le(expiresAt));
    }

    public TransactionInstruction payInvoice(PublicKey client, PublicKey freelancer, PublicKey mint,
                                              String invoiceId) {
        return instruction("pay_invoice", List.of(
                signer(client), readonly(addresses.config()), writable(addresses.invoice(freelancer, invoiceId)),
                readonly(freelancer), readonly(mint), writable(addresses.ata(client, mint)),
                writable(addresses.ata(freelancer, mint)), readonly(SolanaAddresses.TOKEN_PROGRAM)
        ));
    }

    public TransactionInstruction cancelInvoice(PublicKey freelancer, String invoiceId) {
        return instruction("cancel_invoice", List.of(
                signer(freelancer), readonly(addresses.config()),
                writable(addresses.invoice(freelancer, invoiceId))
        ));
    }

    public TransactionInstruction closeInvoice(PublicKey freelancer, String invoiceId) {
        return instruction("close_invoice", List.of(
                writableSigner(freelancer), writable(addresses.invoice(freelancer, invoiceId))
        ));
    }

    public TransactionInstruction requestOfframp(PublicKey freelancer, String withdrawalId,
                                                 String rateId, String amount, PublicKey mint,
                                                 PublicKey treasuryAuthority) {
        return instruction("request_offramp", List.of(
                writableSigner(freelancer), readonly(addresses.config()), readonly(addresses.rate(rateId)),
                readonly(mint), writable(addresses.ata(freelancer, mint)), readonly(treasuryAuthority),
                writable(addresses.ata(treasuryAuthority, mint)),
                writable(addresses.withdrawal(freelancer, withdrawalId)),
                readonly(SolanaAddresses.TOKEN_PROGRAM), readonly(SolanaAddresses.SYSTEM_PROGRAM)
        ), SolanaValueCodec.u64Le(withdrawalId, "withdrawalId"),
                SolanaValueCodec.u64Le(amount, "tokenAmount"));
    }

    public TransactionInstruction recordOfframp(PublicKey oracle, PublicKey freelancer,
                                                String withdrawalId) {
        return instruction("record_offramp", List.of(
                signer(oracle), readonly(addresses.config()),
                writable(addresses.withdrawal(freelancer, withdrawalId))
        ));
    }

    public TransactionInstruction markOfframpFailed(PublicKey oracle, PublicKey freelancer,
                                                     String withdrawalId, byte[] failureHash) {
        return instruction("mark_offramp_failed", List.of(
                signer(oracle), readonly(addresses.config()),
                writable(addresses.withdrawal(freelancer, withdrawalId))
        ), failureHash);
    }

    public TransactionInstruction resolveOfframp(PublicKey admin, PublicKey freelancer,
                                                 String withdrawalId, byte[] resolutionHash) {
        return instruction("resolve_offramp", List.of(
                signer(admin), readonly(addresses.config()),
                writable(addresses.withdrawal(freelancer, withdrawalId))
        ), resolutionHash);
    }

    public TransactionInstruction createAtaIdempotent(PublicKey payer, PublicKey owner, PublicKey mint) {
        return new TransactionInstruction(SolanaAddresses.ASSOCIATED_TOKEN_PROGRAM, List.of(
                writableSigner(payer), writable(addresses.ata(owner, mint)), readonly(owner), readonly(mint),
                readonly(SolanaAddresses.SYSTEM_PROGRAM), readonly(SolanaAddresses.TOKEN_PROGRAM)
        ), new byte[]{1});
    }

    public TransactionInstruction fundMilestoneEscrow(PublicKey client, PublicKey marketplaceAuthority,
            PublicKey freelancer, PublicKey mint, String milestoneId, String amount,
            long fundingExpiresAt, long deliveryDueAt, int reviewWindowHours, int maxRevisions,
            boolean highValueReviewGrace) {
        PublicKey escrow = addresses.milestoneEscrow(milestoneId);
        return instruction("fund_milestone_escrow", List.of(
                writableSigner(client), signer(marketplaceAuthority), readonly(addresses.config()),
                readonly(mint), writable(escrow), writable(addresses.ata(escrow, mint)),
                writable(addresses.ata(client, mint)), readonly(SolanaAddresses.TOKEN_PROGRAM),
                readonly(SolanaAddresses.ASSOCIATED_TOKEN_PROGRAM), readonly(SolanaAddresses.SYSTEM_PROGRAM)
        ), SolanaValueCodec.uuid16(milestoneId), freelancer.toByteArray(),
                SolanaValueCodec.u64Le(amount, "amount"), SolanaValueCodec.i64Le(fundingExpiresAt),
                SolanaValueCodec.i64Le(deliveryDueAt),
                SolanaValueCodec.u16Le(reviewWindowHours, "reviewWindowHours"),
                new byte[]{(byte) maxRevisions}, bool(highValueReviewGrace));
    }

    public TransactionInstruction escrowPartyAction(String name, PublicKey actor,
            String milestoneId, byte[]... args) {
        return instruction(name, List.of(signer(actor), writable(addresses.milestoneEscrow(milestoneId))), args);
    }

    public TransactionInstruction submitEscrowWork(PublicKey freelancer,
            PublicKey marketplaceAuthority, String milestoneId, byte[] evidenceHash) {
        return escrowCoSignedAction("submit_escrow_work", freelancer,
                marketplaceAuthority, milestoneId, evidenceHash);
    }

    public TransactionInstruction escrowCoSignedAction(String name, PublicKey actor,
            PublicKey marketplaceAuthority, String milestoneId, byte[] hash) {
        return instruction(name, List.of(signer(actor),
                signer(marketplaceAuthority), writable(addresses.milestoneEscrow(milestoneId))),
                hash);
    }

    public TransactionInstruction settleMilestoneEscrow(PublicKey actor, String milestoneId,
            PublicKey mint, PublicKey recipient, boolean release, byte[] resolutionHash) {
        PublicKey escrow = addresses.milestoneEscrow(milestoneId);
        return instruction("settle_milestone_escrow", List.of(
                writableSigner(actor), writable(escrow), readonly(mint),
                writable(addresses.ata(escrow, mint)), readonly(recipient),
                writable(addresses.ata(recipient, mint)), readonly(SolanaAddresses.TOKEN_PROGRAM),
                readonly(SolanaAddresses.ASSOCIATED_TOKEN_PROGRAM), readonly(SolanaAddresses.SYSTEM_PROGRAM)
        ), bool(release), resolutionHash);
    }

    public TransactionInstruction refundMutualEscrow(PublicKey client, PublicKey freelancer,
            PublicKey mint, String milestoneId) {
        PublicKey escrow = addresses.milestoneEscrow(milestoneId);
        return instruction("refund_mutual_escrow", List.of(
                writableSigner(client), signer(freelancer), writable(escrow), readonly(mint),
                writable(addresses.ata(escrow, mint)), writable(addresses.ata(client, mint)),
                readonly(SolanaAddresses.TOKEN_PROGRAM)
        ));
    }

    private TransactionInstruction instruction(String name, List<AccountMeta> accounts, byte[]... args) {
        return new TransactionInstruction(addresses.programId(), accounts,
                SolanaValueCodec.instructionData(name, args));
    }

    private AccountMeta writableSigner(PublicKey key) {
        return new AccountMeta(key, true, true);
    }

    private AccountMeta signer(PublicKey key) {
        return new AccountMeta(key, true, false);
    }

    private AccountMeta writable(PublicKey key) {
        return new AccountMeta(key, false, true);
    }

    private AccountMeta readonly(PublicKey key) {
        return new AccountMeta(key, false, false);
    }

    private byte[] bool(boolean value) {
        return new byte[]{(byte) (value ? 1 : 0)};
    }
}
