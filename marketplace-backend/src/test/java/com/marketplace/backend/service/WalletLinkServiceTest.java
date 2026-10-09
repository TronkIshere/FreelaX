package com.marketplace.backend.service;

import com.marketplace.backend.repository.WalletLinkChallengeRepository;
import com.marketplace.backend.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
class WalletLinkServiceTest {
    @Autowired WalletRepository wallets;
    @Autowired WalletLinkChallengeRepository challenges;

    @Test
    void realWalletSignatureBindsOnlyOnceAndRejectsReplay() throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] encoded = pair.getPublic().getEncoded();
        String address = base58(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
        UUID user = UUID.randomUUID();
        WalletLinkService service = new WalletLinkService(wallets, challenges);
        var challenge = service.challenge(user, address);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(challenge.message().getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signer.sign());
        assertEquals(address, service.verify(user, challenge.challengeId(), signature).walletAddress());
        assertEquals(address, service.current(user).walletAddress());
        assertThrows(RuntimeException.class, () -> service.verify(user, challenge.challengeId(), signature));
        assertThrows(RuntimeException.class, () -> service.challenge(UUID.randomUUID(), address));
    }

    private static String base58(byte[] bytes) {
        String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        BigInteger value = new BigInteger(1, bytes);
        StringBuilder result = new StringBuilder();
        while (value.signum() > 0) {
            BigInteger[] qr = value.divideAndRemainder(BigInteger.valueOf(58));
            result.append(alphabet.charAt(qr[1].intValue()));
            value = qr[0];
        }
        for (byte b : bytes) { if (b == 0) result.append('1'); else break; }
        return result.reverse().toString();
    }
}
