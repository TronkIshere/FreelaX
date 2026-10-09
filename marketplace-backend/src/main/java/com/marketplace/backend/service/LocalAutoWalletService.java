package com.marketplace.backend.service;

import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.entity.EscrowContract;
import com.marketplace.backend.entity.PaymentFlow;
import com.marketplace.backend.entity.PaymentFlowEvidence;
import com.marketplace.backend.entity.PaymentFlowStep;
import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.repository.EscrowContractRepository;
import com.marketplace.backend.repository.PaymentFlowEvidenceRepository;
import com.marketplace.backend.repository.PaymentFlowRepository;
import com.marketplace.backend.repository.PaymentFlowStepRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

/** Local demo custody. No private key or transaction internals are returned to the browser. */
@Service
public class LocalAutoWalletService {
    private static final String BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final byte[] ED25519_PKCS8_PREFIX = {
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06,
            0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
    };
    private final UserRepository users;
    private final WalletRepository wallets;
    private final EscrowContractRepository escrows;
    private final PaymentFlowRepository flows;
    private final PaymentFlowStepRepository steps;
    private final PaymentFlowEvidenceRepository evidence;
    private final SolanaCprClient solana;
    private final boolean enabled;
    private final SecretKeySpec encryptionKey;
    private final Map<String, PrivateKey> seededKeys;
    private final SecureRandom random = new SecureRandom();

    public record ConnectedWallet(String walletAddress) { }
    public record SignedTransaction(String transactionBase64) { }

    public LocalAutoWalletService(UserRepository users, WalletRepository wallets,
                                  EscrowContractRepository escrows, PaymentFlowRepository flows,
                                  PaymentFlowStepRepository steps, PaymentFlowEvidenceRepository evidence,
                                  SolanaCprClient solana, Environment environment,
                                  @Value("${solana-cpr.network:localnet}") String network,
                                  @Value("${security.jwt.secret}") String jwtSecret,
                                  @Value("${SOLANA_LOCAL_PRIVATE_KEYS:}") String configuredKeys) {
        this.users = users;
        this.wallets = wallets;
        this.escrows = escrows;
        this.flows = flows;
        this.steps = steps;
        this.evidence = evidence;
        this.solana = solana;
        this.enabled = environment.acceptsProfiles(Profiles.of("dev")) && "localnet".equals(network);
        this.encryptionKey = new SecretKeySpec(sha256(("FreelaX local wallet v1:" + jwtSecret)
                .getBytes(StandardCharsets.UTF_8)), "AES");
        this.seededKeys = seededKeys(configuredKeys);
    }

    @Transactional
    public ConnectedWallet connect(UUID userId) {
        requireLocalDemo();
        users.findWithLockById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Wallet wallet = wallets.findFirstByUserIdOrderByIdAsc(userId).orElse(null);
        if (wallet == null) {
            wallet = new Wallet();
            wallet.setUserId(userId);
            newDemoKey(wallet);
            wallets.saveAndFlush(wallet);
        } else if (wallet.getDemoPrivateKey() == null && !seededKeys.containsKey(wallet.getPublicKey())) {
            recoverUnfundedLocalWallet(userId, wallet);
        }
        return new ConnectedWallet(wallet.getPublicKey());
    }

    public boolean isLocalDemoEnabled() { return enabled; }

    /** Rotate only before escrow funding. An old, inaccessible local on-ramp receipt is audited
     * and the confirmed mock USD payment is delivered again to the new wallet by reconciliation. */
    private void recoverUnfundedLocalWallet(UUID userId, Wallet wallet) {
        String previous = wallet.getPublicKey();
        List<EscrowContract> stale = escrows.findByClientWalletOrFreelancerWallet(previous, previous);
        for (EscrowContract escrow : stale) {
            if (escrow.getFundSignature() != null
                    || solana.findEscrow(escrow.getMilestoneId().toString()).isPresent())
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Existing funded wallet needs its original signer");
        }
        List<PaymentFlow> clientFlows = flows.findByClientId(userId);
        for (PaymentFlow flow : clientFlows) {
            Map<String, PaymentFlowStep> byKind = new HashMap<>();
            for (PaymentFlowStep step : steps.findByPaymentFlowIdOrderByCreatedAtAsc(flow.getId()))
                byKind.put(step.getKind(), step);
            for (String kind : List.of("ESCROW", "USDC_RELEASE", "USDC_REFUND", "WITHDRAWAL")) {
                PaymentFlowStep step = byKind.get(kind);
                if (step != null && !"NOT_STARTED".equals(step.getStatus()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Existing funded wallet needs its original signer");
            }
            PaymentFlowStep clientUsdc = byKind.get("CLIENT_USDC");
            if (clientUsdc == null) continue;
            if ("CONFIRMED".equals(clientUsdc.getStatus())) {
                if (flow.getFundingExpiresAt() == null
                        || !Instant.now().isBefore(flow.getFundingExpiresAt()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Existing payment can no longer be moved to an automatic wallet");
                evidence.save(new PaymentFlowEvidence(flow, "LOCAL_WALLET_RECOVERY", "PENDING",
                        null, clientUsdc.getReference(), "LOCAL_VALIDATOR_DEMO",
                        clientUsdc.getAmount(), clientUsdc.getCurrency()));
                clientUsdc.setStatus("PENDING");
                clientUsdc.setAmount(null);
                clientUsdc.setCurrency(null);
                clientUsdc.setProvider(null);
                clientUsdc.setReference(null);
                clientUsdc.setIdempotencyKey(null);
                clientUsdc.setTransactionSignature(null);
                clientUsdc.setSubmittedAt(null);
                clientUsdc.setAccountAddress(null);
                clientUsdc.setTokenAccount(null);
                clientUsdc.setEvidenceSource(null);
                clientUsdc.setRetryAfter(null);
                clientUsdc.setConfirmedAt(null);
            } else if (clientUsdc.getTransactionSignature() != null
                    || clientUsdc.getAccountAddress() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A previous wallet payment is still being checked");
            }
        }
        escrows.deleteAll(stale);
        wallet.setDemoPreviousPublicKey(previous);
        newDemoKey(wallet);
        wallets.saveAndFlush(wallet);
    }

    private void newDemoKey(Wallet wallet) {
        try {
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] publicEncoding = pair.getPublic().getEncoded();
            wallet.setPublicKey(base58(Arrays.copyOfRange(publicEncoding,
                    publicEncoding.length - 32, publicEncoding.length)));
            wallet.setDemoPrivateKey(encrypt(pair.getPrivate().getEncoded()));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create local demo wallet", exception);
        }
    }

    @Transactional
    public SignedTransaction sign(UUID userId, String transactionBase64) {
        ConnectedWallet connected = connect(userId);
        Wallet wallet = wallets.findFirstByUserIdOrderByIdAsc(userId).orElseThrow();
        PrivateKey privateKey = wallet.getDemoPrivateKey() == null
                ? seededKeys.get(connected.walletAddress()) : decrypt(wallet.getDemoPrivateKey());
        byte[] serialized;
        try { serialized = Base64.getDecoder().decode(transactionBase64); }
        catch (RuntimeException exception) { throw invalidTransaction(); }
        if (serialized.length < 100 || serialized.length > 16_384) throw invalidTransaction();
        ShortVec signatures = shortVec(serialized, 0);
        if (signatures.value() < 1 || signatures.value() > 16) throw invalidTransaction();
        int messageStart = signatures.next() + 64 * signatures.value();
        if (messageStart + 4 >= serialized.length) throw invalidTransaction();
        byte[] message = Arrays.copyOfRange(serialized, messageStart, serialized.length);
        int requiredSignatures = message[0] & 0xff;
        ShortVec accounts = shortVec(message, 3);
        if (requiredSignatures != signatures.value() || accounts.value() < requiredSignatures
                || accounts.next() + 32 * accounts.value() > message.length) throw invalidTransaction();
        byte[] expectedPublicKey = decodeBase58(connected.walletAddress());
        int signerIndex = -1;
        for (int i = 0; i < requiredSignatures; i++) {
            int offset = accounts.next() + 32 * i;
            if (Arrays.equals(expectedPublicKey, Arrays.copyOfRange(message, offset, offset + 32))) {
                signerIndex = i;
                break;
            }
        }
        if (signerIndex < 0) throw invalidTransaction();
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(message);
            byte[] signature = signer.sign();
            System.arraycopy(signature, 0, serialized, signatures.next() + signerIndex * 64, 64);
            return new SignedTransaction(Base64.getEncoder().encodeToString(serialized));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign local demo transaction", exception);
        }
    }

    private void requireLocalDemo() {
        if (!enabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private ResponseStatusException invalidTransaction() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Solana transaction");
    }

    private record ShortVec(int value, int next) { }
    private ShortVec shortVec(byte[] bytes, int start) {
        int value = 0;
        int position = start;
        for (int shift = 0; shift <= 14; shift += 7) {
            if (position >= bytes.length) throw invalidTransaction();
            int octet = bytes[position++] & 0xff;
            value |= (octet & 0x7f) << shift;
            if ((octet & 0x80) == 0) return new ShortVec(value, position);
        }
        throw invalidTransaction();
    }

    private byte[] encrypt(byte[] plaintext) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] encrypted = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, encrypted, nonce.length, ciphertext.length);
            return encrypted;
        } catch (Exception exception) { throw new IllegalStateException("Could not encrypt local wallet", exception); }
    }

    private PrivateKey decrypt(byte[] encrypted) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey,
                    new GCMParameterSpec(128, Arrays.copyOfRange(encrypted, 0, 12)));
            return KeyFactory.getInstance("Ed25519").generatePrivate(
                    new PKCS8EncodedKeySpec(cipher.doFinal(encrypted, 12, encrypted.length - 12)));
        } catch (Exception exception) { throw new IllegalStateException("Could not read local wallet", exception); }
    }

    private Map<String, PrivateKey> seededKeys(String configured) {
        Map<String, PrivateKey> keys = new HashMap<>();
        if (configured == null || configured.isBlank()) return keys;
        try {
            for (String value : configured.split(";")) {
                if (value.isBlank()) continue;
                byte[] secret = decodeBase58(value.trim());
                if (secret.length != 64) throw new IllegalArgumentException("Demo wallet key must be 64 bytes");
                byte[] pkcs8 = Arrays.copyOf(ED25519_PKCS8_PREFIX, ED25519_PKCS8_PREFIX.length + 32);
                System.arraycopy(secret, 0, pkcs8, ED25519_PKCS8_PREFIX.length, 32);
                keys.put(base58(Arrays.copyOfRange(secret, 32, 64)), KeyFactory.getInstance("Ed25519")
                        .generatePrivate(new PKCS8EncodedKeySpec(pkcs8)));
                Arrays.fill(secret, (byte) 0);
                Arrays.fill(pkcs8, (byte) 0);
            }
            return Map.copyOf(keys);
        } catch (Exception exception) { throw new IllegalStateException("Invalid local demo wallet key", exception); }
    }

    private static byte[] sha256(byte[] input) {
        try { return MessageDigest.getInstance("SHA-256").digest(input); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private byte[] decodeBase58(String value) {
        java.math.BigInteger number = java.math.BigInteger.ZERO;
        for (char c : value.toCharArray()) {
            int digit = BASE58.indexOf(c);
            if (digit < 0) throw new IllegalArgumentException("Invalid Base58");
            number = number.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(digit));
        }
        byte[] integer = number.signum() == 0 ? new byte[0] : number.toByteArray();
        if (integer.length > 0 && integer[0] == 0) integer = Arrays.copyOfRange(integer, 1, integer.length);
        int zeros = 0;
        while (zeros < value.length() && value.charAt(zeros) == '1') zeros++;
        byte[] raw = new byte[zeros + integer.length];
        System.arraycopy(integer, 0, raw, zeros, integer.length);
        return raw;
    }

    private String base58(byte[] raw) {
        java.math.BigInteger number = new java.math.BigInteger(1, raw);
        StringBuilder encoded = new StringBuilder();
        while (number.signum() > 0) {
            java.math.BigInteger[] parts = number.divideAndRemainder(java.math.BigInteger.valueOf(58));
            encoded.append(BASE58.charAt(parts[1].intValue()));
            number = parts[0];
        }
        for (byte b : raw) { if (b != 0) break; encoded.append('1'); }
        return encoded.reverse().toString();
    }
}
