package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.entity.TxRecord;
import com.freelax.avalanchegateway.signer.Signature;
import com.freelax.avalanchegateway.signer.Signer;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

/**
 * Builds and signs EIP-1559 transactions using only a {@link Signer} (digest signing). The chain id is taken from the
 * persisted record, which was written from the signer's verified chain id; a mismatch is refused.
 */
public final class TransactionSigning {

    private TransactionSigning() {
    }

    public record Signed(String rawHex, String txHash) {
    }

    public static Signed sign(TxRecord record, Signer signer) {
        if (signer.chainId() != record.chainId()) {
            throw new IllegalStateException("the signer is bound to chain " + signer.chainId() + " but the record is for chain " + record.chainId());
        }
        if (!signer.address().equalsIgnoreCase(record.signerAddress())) {
            throw new IllegalStateException("the signer address differs from the address the record was created for");
        }
        RawTransaction raw = RawTransaction.createTransaction(record.chainId(), BigInteger.valueOf(record.nonce()),
                BigInteger.valueOf(record.gasLimit()), record.toAddress(), BigInteger.ZERO, record.data(),
                record.maxPriorityFeePerGas(), record.maxFeePerGas());
        byte[] unsigned = TransactionEncoder.encode(raw);
        Signature signature = signer.sign(Hash.sha3(unsigned));
        byte[] signed = TransactionEncoder.encode(raw, new Sign.SignatureData((byte) signature.v(), signature.r(), signature.s()));
        return new Signed(Numeric.toHexString(signed), Numeric.toHexString(Hash.sha3(signed)));
    }
}
