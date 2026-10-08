package com.freelax.solanagateway.solana;

import com.freelax.solanagateway.api.Responses.ConfigDto;
import com.freelax.solanagateway.api.Responses.InvoiceDto;
import com.freelax.solanagateway.api.Responses.EscrowDto;
import com.freelax.solanagateway.api.Responses.MockOnrampReceiptDto;
import com.freelax.solanagateway.api.Responses.RateSnapshotDto;
import com.freelax.solanagateway.api.Responses.WithdrawalDto;
import org.springframework.stereotype.Component;

@Component
public class AnchorAccountDecoder {

    public ConfigDto config(String address, byte[] data) {
        var reader = new BorshReader(data, "Config");
        return new ConfigDto(address, reader.publicKey(), reader.publicKey(), reader.publicKey(),
                reader.publicKey(), reader.publicKey(), reader.i64(), reader.publicKey(), reader.u64(),
                reader.bool(), reader.bool(), reader.u8());
    }

    public InvoiceDto invoice(String address, byte[] data) {
        var reader = new BorshReader(data, "Invoice");
        String invoiceId = reader.u64();
        String freelancer = reader.publicKey();
        String client = reader.publicKey();
        String amount = reader.u64();
        String mint = reader.publicKey();
        String rateSnapshot = reader.publicKey();
        String expiresAt = reader.i64();
        String status = enumValue(reader.u8(), "Pending", "Paid", "Cancelled");
        return new InvoiceDto(address, invoiceId, freelancer, client, amount, mint, rateSnapshot,
                expiresAt, status, reader.i64(), reader.optionI64(), reader.u8());
    }

    public EscrowDto escrow(String address, byte[] data) {
        var reader = new BorshReader(data, "MilestoneEscrow");
        return new EscrowDto(address, reader.uuid16(), reader.publicKey(), reader.publicKey(),
                reader.publicKey(), reader.publicKey(), reader.u64(), reader.i64(),
                reader.i64(), reader.i64(),
                reader.optionI64(), reader.bool(), reader.i64(), reader.optionI64(),
                reader.optionHash32(), reader.u8(), reader.u8(), reader.u8(),
                enumValue(reader.u8(), "Funded", "Submitted", "Revision", "Disputed", "Released", "Refunded"),
                reader.i64(), reader.optionI64(), reader.optionHash32(),
                reader.optionPublicKey(), reader.optionI64(), reader.optionHash32(),
                reader.optionHash32(), reader.u8(),
                null, null);
    }

    public RateSnapshotDto rate(String address, byte[] data) {
        var reader = new BorshReader(data, "RateSnapshot");
        return new RateSnapshotDto(address, reader.u64(), reader.u64(), reader.u64(), reader.u64(),
                reader.i64(), reader.i64(), reader.hash32(), reader.publicKey(), reader.u8());
    }

    public WithdrawalDto withdrawal(String address, byte[] data) {
        var reader = new BorshReader(data, "WithdrawalRecord");
        String withdrawalId = reader.u64();
        String freelancer = reader.publicKey();
        String tokenAmount = reader.u64();
        String mint = reader.publicKey();
        String treasury = reader.publicKey();
        String rateSnapshot = reader.publicKey();
        String fiatAmount = reader.u64();
        String status = enumValue(reader.u8(), "Pending", "FailedPendingReview", "Completed");
        return new WithdrawalDto(address, withdrawalId, freelancer, tokenAmount, mint, treasury,
                rateSnapshot, fiatAmount, status, reader.i64(), reader.optionI64(),
                reader.optionHash32(), reader.optionI64(), reader.optionHash32(),
                reader.optionI64(), reader.optionPublicKey(), reader.u8());
    }

    public MockOnrampReceiptDto receipt(String address, byte[] data) {
        var reader = new BorshReader(data, "MockOnrampReceipt");
        return new MockOnrampReceiptDto(address, reader.u64(), reader.publicKey(), reader.publicKey(),
                reader.publicKey(), reader.publicKey(), reader.u64(), reader.u64(), reader.publicKey(),
                reader.i64(), reader.u8());
    }

    private String enumValue(int ordinal, String... values) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Unknown Anchor enum ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
