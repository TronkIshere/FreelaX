package com.marketplace.backend.service;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.repository.EscrowContractRepository;
import com.marketplace.backend.repository.PaymentFlowEvidenceRepository;
import com.marketplace.backend.repository.PaymentFlowRepository;
import com.marketplace.backend.repository.PaymentFlowStepRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalAutoWalletSecurityTest {
    private final UserRepository users = mock(UserRepository.class);
    private final WalletRepository wallets = mock(WalletRepository.class);

    private LocalAutoWalletService service(boolean dev, String network, String seededKeys, String walletKey) {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(dev);
        return new LocalAutoWalletService(users, wallets, mock(EscrowContractRepository.class),
                mock(PaymentFlowRepository.class), mock(PaymentFlowStepRepository.class),
                mock(PaymentFlowEvidenceRepository.class), mock(SolanaCprClient.class), environment,
                network, "test-only-secret", seededKeys, walletKey);
    }

    @Test
    void refusesSeededKeysOutsideDevProfile() {
        byte[] secret = new byte[64];
        new SecureRandom().nextBytes(secret);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service(false, "localnet", encodeBase58(secret), ""));
        assertTrue(failure.getMessage().contains("dev profile on localnet"));
    }

    @Test
    void refusesSeededKeysOnNonLocalNetwork() {
        byte[] secret = new byte[64];
        new SecureRandom().nextBytes(secret);
        assertThrows(IllegalStateException.class,
                () -> service(true, "mainnet-beta", encodeBase58(secret), ""));
    }

    @Test
    void refusesWalletEncryptionKeyOutsideDevLocalnet() {
        assertThrows(IllegalStateException.class, () -> service(false, "localnet", "", "operator-key"));
        assertThrows(IllegalStateException.class, () -> service(true, "devnet", "", "operator-key"));
    }

    @Test
    void startsWithoutLocalSecretsOutsideDev() {
        LocalAutoWalletService service = service(false, "mainnet-beta", "", "");
        assertFalse(service.isLocalDemoEnabled());
    }

    @Test
    void walletCreatedWithLegacyKeyStillSignsAfterEncryptionKeyIsConfigured() throws Exception {
        UUID id = UUID.randomUUID();
        when(users.findWithLockById(id)).thenReturn(Optional.of(new User()));
        AtomicReference<Wallet> saved = new AtomicReference<>();
        when(wallets.findFirstByUserIdOrderByIdAsc(id)).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(wallets.saveAndFlush(any(Wallet.class))).thenAnswer(call -> {
            saved.set(call.getArgument(0));
            return call.getArgument(0);
        });
        String address = service(true, "localnet", "", "").connect(id).walletAddress();
        byte[] legacyCiphertext = saved.get().getDemoPrivateKey().clone();

        LocalAutoWalletService migrated = service(true, "localnet", "", "operator-key");
        String signed = migrated.sign(id, unsignedTransaction(address)).transactionBase64();

        byte[] signature = Arrays.copyOfRange(Base64.getDecoder().decode(signed), 1, 65);
        assertFalse(Arrays.equals(signature, new byte[64]));
        assertArrayEquals(legacyCiphertext, saved.get().getDemoPrivateKey());
    }

    @Test
    void walletCreatedWithConfiguredKeyIsUnreadableWithoutIt() throws Exception {
        UUID id = UUID.randomUUID();
        when(users.findWithLockById(id)).thenReturn(Optional.of(new User()));
        AtomicReference<Wallet> saved = new AtomicReference<>();
        when(wallets.findFirstByUserIdOrderByIdAsc(id)).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(wallets.saveAndFlush(any(Wallet.class))).thenAnswer(call -> {
            saved.set(call.getArgument(0));
            return call.getArgument(0);
        });
        String address = service(true, "localnet", "", "operator-key").connect(id).walletAddress();

        LocalAutoWalletService withoutKey = service(true, "localnet", "", "");
        assertThrows(IllegalStateException.class, () -> withoutKey.sign(id, unsignedTransaction(address)));
    }

    private static String unsignedTransaction(String address) {
        byte[] publicKey = decodeBase58(address);
        byte[] message = new byte[3 + 1 + 32 + 32 + 1];
        message[0] = 1;
        message[3] = 1;
        System.arraycopy(publicKey, 0, message, 4, 32);
        byte[] unsigned = new byte[1 + 64 + message.length];
        unsigned[0] = 1;
        System.arraycopy(message, 0, unsigned, 65, message.length);
        return Base64.getEncoder().encodeToString(unsigned);
    }

    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private static String encodeBase58(byte[] bytes) {
        BigInteger number = new BigInteger(1, bytes);
        StringBuilder value = new StringBuilder();
        while (number.signum() > 0) {
            BigInteger[] parts = number.divideAndRemainder(BigInteger.valueOf(58));
            value.append(ALPHABET.charAt(parts[1].intValue()));
            number = parts[0];
        }
        for (byte b : bytes) { if (b != 0) break; value.append('1'); }
        return value.reverse().toString();
    }

    private static byte[] decodeBase58(String value) {
        BigInteger number = BigInteger.ZERO;
        for (char c : value.toCharArray()) {
            number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(ALPHABET.indexOf(c)));
        }
        byte[] raw = number.toByteArray();
        byte[] stripped = raw.length > 1 && raw[0] == 0 ? Arrays.copyOfRange(raw, 1, raw.length) : raw;
        int leadingOnes = 0;
        while (leadingOnes < value.length() && value.charAt(leadingOnes) == '1') leadingOnes++;
        byte[] result = new byte[leadingOnes + stripped.length];
        System.arraycopy(stripped, 0, result, leadingOnes, stripped.length);
        return result.length == 32 ? result : Arrays.copyOfRange(result, result.length - 32, result.length);
    }
}
