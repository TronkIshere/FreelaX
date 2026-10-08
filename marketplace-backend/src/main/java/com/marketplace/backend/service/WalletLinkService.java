package com.marketplace.backend.service;

import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.entity.WalletLinkChallenge;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.WalletLinkChallengeRepository;
import com.marketplace.backend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletLinkService {
    private static final String BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final byte[] ED25519_X509_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    private final WalletRepository wallets;
    private final WalletLinkChallengeRepository challenges;

    public record Challenge(UUID challengeId, String walletAddress, String message,
            Instant expiresAt) { }
    public record BoundWallet(String walletAddress) { }

    @Transactional(readOnly = true)
    public BoundWallet current(UUID userId) {
        return wallets.findFirstByUserIdOrderByIdAsc(userId)
                .map(wallet -> new BoundWallet(wallet.getPublicKey())).orElse(null);
    }

    @Transactional
    public Challenge challenge(UUID userId, String walletAddress) {
        decodeBase58(walletAddress);
        Wallet existing = wallets.findFirstByUserIdOrderByIdAsc(userId).orElse(null);
        if (existing != null && !existing.getPublicKey().equals(walletAddress)) throw invalid();
        if (wallets.existsByPublicKey(walletAddress) && existing == null) throw invalid();
        WalletLinkChallenge row = new WalletLinkChallenge();
        row.setUserId(userId);
        row.setWalletAddress(walletAddress);
        row.setExpiresAt(Instant.now().plusSeconds(300));
        row.setMessage("FreelaX wallet binding\nUser: " + userId
                + "\nWallet: " + walletAddress + "\nNonce: " + UUID.randomUUID()
                + "\nExpires: " + row.getExpiresAt());
        challenges.saveAndFlush(row);
        return new Challenge(row.getId(), walletAddress, row.getMessage(), row.getExpiresAt());
    }

    @Transactional
    public BoundWallet verify(UUID userId, UUID challengeId, String signatureBase64) {
        WalletLinkChallenge row = challenges.findWithLockById(challengeId).orElseThrow(this::invalid);
        if (!userId.equals(row.getUserId()) || row.getUsedAt() != null
                || !Instant.now().isBefore(row.getExpiresAt())
                || !validSignature(row.getWalletAddress(), row.getMessage(), signatureBase64)) throw invalid();
        Wallet existing = wallets.findFirstByUserIdOrderByIdAsc(userId).orElse(null);
        if (existing != null && !existing.getPublicKey().equals(row.getWalletAddress())) throw invalid();
        if (existing == null) {
            if (wallets.existsByPublicKey(row.getWalletAddress())) throw invalid();
            Wallet wallet = new Wallet();
            wallet.setUserId(userId);
            wallet.setPublicKey(row.getWalletAddress());
            wallets.saveAndFlush(wallet);
        }
        row.setUsedAt(Instant.now());
        return new BoundWallet(row.getWalletAddress());
    }

    private boolean validSignature(String address, String message, String encoded) {
        try {
            byte[] raw = decodeBase58(address);
            byte[] publicKey = new byte[ED25519_X509_PREFIX.length + 32];
            System.arraycopy(ED25519_X509_PREFIX, 0, publicKey, 0, ED25519_X509_PREFIX.length);
            System.arraycopy(raw, 0, publicKey, ED25519_X509_PREFIX.length, 32);
            byte[] signature = Base64.getDecoder().decode(encoded);
            if (signature.length != 64) return false;
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(publicKey)));
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signature);
        } catch (Exception exception) { return false; }
    }

    private byte[] decodeBase58(String value) {
        if (value == null || value.length() < 32 || value.length() > 44) throw invalid();
        BigInteger decoded = BigInteger.ZERO;
        for (int i = 0; i < value.length(); i++) {
            int digit = BASE58.indexOf(value.charAt(i));
            if (digit < 0) throw invalid();
            decoded = decoded.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
        }
        int zeroes = 0;
        while (zeroes < value.length() && value.charAt(zeroes) == '1') zeroes++;
        byte[] integer = decoded.signum() == 0 ? new byte[0] : decoded.toByteArray();
        int sign = integer.length > 0 && integer[0] == 0 ? 1 : 0;
        if (zeroes + integer.length - sign != 32) throw invalid();
        byte[] raw = new byte[32];
        System.arraycopy(integer, sign, raw, zeroes, integer.length - sign);
        return raw;
    }

    private ApplicationException invalid() {
        return new ApplicationException(ErrorCode.INVALID_DATA);
    }
}
