package com.marketplace.backend.service;

import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.entity.EscrowContract;
import com.marketplace.backend.entity.PaymentFlow;
import com.marketplace.backend.entity.PaymentFlowStep;
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
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.EdECPrivateKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalAutoWalletServiceTest {
    @Test
    void createsEncryptedWalletAndSignsOnlyItsRequiredSignature() throws Exception {
        UserRepository users = mock(UserRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        Environment environment = mock(Environment.class);
        UUID id = UUID.randomUUID();
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        when(users.findWithLockById(id)).thenReturn(Optional.of(new User()));
        AtomicReference<Wallet> saved = new AtomicReference<>();
        when(wallets.findFirstByUserIdOrderByIdAsc(id)).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(wallets.saveAndFlush(any(Wallet.class))).thenAnswer(call -> {
            Wallet wallet = call.getArgument(0);
            saved.set(wallet);
            return wallet;
        });
        LocalAutoWalletService service = new LocalAutoWalletService(users, wallets,
                mock(EscrowContractRepository.class), mock(PaymentFlowRepository.class),
                mock(PaymentFlowStepRepository.class), mock(PaymentFlowEvidenceRepository.class),
                mock(SolanaCprClient.class), environment,
                "localnet", "test-only-secret", "");

        String address = service.connect(id).walletAddress();
        assertNotNull(saved.get().getDemoPrivateKey());
        assertFalse(Arrays.equals(saved.get().getDemoPrivateKey(), new byte[48]));
        byte[] publicKey = decodeBase58(address);
        assertEquals(32, publicKey.length);

        byte[] message = new byte[3 + 1 + 32 + 32 + 1];
        message[0] = 1;
        message[3] = 1;
        System.arraycopy(publicKey, 0, message, 4, 32);
        byte[] unsigned = new byte[1 + 64 + message.length];
        unsigned[0] = 1;
        System.arraycopy(message, 0, unsigned, 65, message.length);
        byte[] signed = Base64.getDecoder().decode(service.sign(id,
                Base64.getEncoder().encodeToString(unsigned)).transactionBase64());
        Signature verifier = Signature.getInstance("Ed25519");
        byte[] prefix = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
        byte[] encoded = Arrays.copyOf(prefix, prefix.length + 32);
        System.arraycopy(publicKey, 0, encoded, prefix.length, 32);
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded)));
        verifier.update(message);
        assertTrue(verifier.verify(Arrays.copyOfRange(signed, 1, 65)));
        assertEquals(address, service.connect(id).walletAddress());
        verify(wallets, times(1)).saveAndFlush(any(Wallet.class));
    }

    @Test
    void connectsAlreadyBoundSeedWalletWithoutCreatingAnotherAddress() throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] publicEncoding = pair.getPublic().getEncoded();
        byte[] publicKey = Arrays.copyOfRange(publicEncoding, publicEncoding.length - 32, publicEncoding.length);
        byte[] secret = new byte[64];
        System.arraycopy(((EdECPrivateKey) pair.getPrivate()).getBytes().orElseThrow(), 0, secret, 0, 32);
        System.arraycopy(publicKey, 0, secret, 32, 32);
        String address = encodeBase58(publicKey);
        UserRepository users = mock(UserRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        Environment environment = mock(Environment.class);
        UUID id = UUID.randomUUID();
        Wallet bound = new Wallet();
        bound.setUserId(id);
        bound.setPublicKey(address);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        when(users.findWithLockById(id)).thenReturn(Optional.of(new User()));
        when(wallets.findFirstByUserIdOrderByIdAsc(id)).thenReturn(Optional.of(bound));
        LocalAutoWalletService service = new LocalAutoWalletService(users, wallets,
                mock(EscrowContractRepository.class), mock(PaymentFlowRepository.class),
                mock(PaymentFlowStepRepository.class), mock(PaymentFlowEvidenceRepository.class),
                mock(SolanaCprClient.class), environment,
                "localnet", "test-only-secret", encodeBase58(secret));

        assertEquals(address, service.connect(id).walletAddress());
        byte[] message = new byte[3 + 1 + 32 + 32 + 1];
        message[0] = 1;
        message[3] = 1;
        System.arraycopy(publicKey, 0, message, 4, 32);
        byte[] unsigned = new byte[1 + 64 + message.length];
        unsigned[0] = 1;
        System.arraycopy(message, 0, unsigned, 65, message.length);
        byte[] signed = Base64.getDecoder().decode(service.sign(id,
                Base64.getEncoder().encodeToString(unsigned)).transactionBase64());
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pair.getPublic());
        verifier.update(message);
        assertTrue(verifier.verify(Arrays.copyOfRange(signed, 1, 65)));
        verify(wallets, never()).saveAndFlush(any(Wallet.class));
    }

    @Test
    void recoversUnfundedLocalJobAndQueuesPaidOnrampForNewWallet() {
        UserRepository users = mock(UserRepository.class);
        WalletRepository wallets = mock(WalletRepository.class);
        EscrowContractRepository escrows = mock(EscrowContractRepository.class);
        PaymentFlowRepository flows = mock(PaymentFlowRepository.class);
        PaymentFlowStepRepository steps = mock(PaymentFlowStepRepository.class);
        PaymentFlowEvidenceRepository evidence = mock(PaymentFlowEvidenceRepository.class);
        SolanaCprClient solana = mock(SolanaCprClient.class);
        Environment environment = mock(Environment.class);
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();
        String oldAddress = "7ivL5T9nkMBjJyt8Mvaias1bB8Huizv89LcrT4ALmeG4";
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setPublicKey(oldAddress);
        EscrowContract stale = new EscrowContract();
        stale.setMilestoneId(milestoneId);
        PaymentFlow flow = new PaymentFlow();
        flow.setId(flowId);
        flow.setJobId(UUID.randomUUID());
        flow.setContractId(UUID.randomUUID());
        flow.setMilestoneId(milestoneId);
        flow.setFundingExpiresAt(Instant.now().plusSeconds(3600));
        PaymentFlowStep usdc = new PaymentFlowStep();
        usdc.setKind("CLIENT_USDC");
        usdc.setStatus("CONFIRMED");
        usdc.setAmount(new BigDecimal("120.000000"));
        usdc.setCurrency("USDC");
        usdc.setReference("old-receipt");
        usdc.setTransactionSignature("old-signature");
        PaymentFlowStep escrow = new PaymentFlowStep();
        escrow.setKind("ESCROW");
        escrow.setStatus("NOT_STARTED");
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        when(users.findWithLockById(userId)).thenReturn(Optional.of(new User()));
        when(wallets.findFirstByUserIdOrderByIdAsc(userId)).thenReturn(Optional.of(wallet));
        when(escrows.findByClientWalletOrFreelancerWallet(oldAddress, oldAddress)).thenReturn(List.of(stale));
        when(solana.findEscrow(milestoneId.toString())).thenReturn(Optional.empty());
        when(flows.findByClientId(userId)).thenReturn(List.of(flow));
        when(steps.findByPaymentFlowIdOrderByCreatedAtAsc(flowId)).thenReturn(List.of(usdc, escrow));
        LocalAutoWalletService service = new LocalAutoWalletService(users, wallets, escrows, flows,
                steps, evidence, solana, environment, "localnet", "test-only-secret", "");

        String address = service.connect(userId).walletAddress();
        assertNotEquals(oldAddress, address);
        assertEquals(oldAddress, wallet.getDemoPreviousPublicKey());
        assertNotNull(wallet.getDemoPrivateKey());
        assertEquals("PENDING", usdc.getStatus());
        assertNull(usdc.getReference());
        assertNull(usdc.getTransactionSignature());
        verify(escrows).deleteAll(List.of(stale));
        verify(evidence).save(any());
    }

    private static String encodeBase58(byte[] bytes) {
        String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        BigInteger number = new BigInteger(1, bytes);
        StringBuilder value = new StringBuilder();
        while (number.signum() > 0) {
            BigInteger[] parts = number.divideAndRemainder(BigInteger.valueOf(58));
            value.append(alphabet.charAt(parts[1].intValue()));
            number = parts[0];
        }
        for (byte b : bytes) { if (b != 0) break; value.append('1'); }
        return value.reverse().toString();
    }

    private static byte[] decodeBase58(String value) {
        String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        BigInteger number = BigInteger.ZERO;
        for (char c : value.toCharArray())
            number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(alphabet.indexOf(c)));
        byte[] integer = number.toByteArray();
        if (integer[0] == 0) integer = Arrays.copyOfRange(integer, 1, integer.length);
        byte[] raw = new byte[32];
        System.arraycopy(integer, 0, raw, 32 - integer.length, integer.length);
        return raw;
    }
}
