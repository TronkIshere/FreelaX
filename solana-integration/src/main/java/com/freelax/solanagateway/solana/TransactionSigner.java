package com.freelax.solanagateway.solana;

public interface TransactionSigner {

    String publicKey();

    byte[] sign(byte[] message);
}
