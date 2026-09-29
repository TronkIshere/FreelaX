package com.marketplace.backend.configuration;

import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DemoWalletSeeder {
    private static final String BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private final WalletRepository walletRepository;
    private final DemoWalletProperties properties;

    public void seed(User client, User freelancer) {
        String clientKey = properties.getClientPublicKey();
        String freelancerKey = properties.getFreelancerPublicKey();
        validateIfPresent("DEMO_CLIENT_SOLANA_PUBLIC_KEY", clientKey);
        validateIfPresent("DEMO_FREELANCER_SOLANA_PUBLIC_KEY", freelancerKey);
        if (StringUtils.hasText(clientKey) && clientKey.equals(freelancerKey)) {
            throw new IllegalStateException("Demo Client and Freelancer must use different Solana public keys");
        }
        seedOne(client, clientKey, "DEMO_CLIENT_SOLANA_PUBLIC_KEY");
        seedOne(freelancer, freelancerKey, "DEMO_FREELANCER_SOLANA_PUBLIC_KEY");
    }

    private void seedOne(User user, String publicKey, String setting) {
        if (!StringUtils.hasText(publicKey)) {
            log.warn("{} is absent; skipping demo Wallet seed for this role", setting);
            return;
        }
        Optional<Wallet> current = walletRepository.findFirstByUserIdOrderByIdAsc(user.getId());
        if (current.isPresent()) {
            if (!publicKey.equals(current.get().getPublicKey())) {
                throw new IllegalStateException("Existing demo Wallet differs from " + setting);
            }
            return;
        }
        if (walletRepository.existsByPublicKey(publicKey)) {
            throw new IllegalStateException(setting + " is already mapped to another user");
        }
        Wallet wallet = new Wallet();
        wallet.setUserId(user.getId());
        wallet.setPublicKey(publicKey);
        walletRepository.save(wallet);
        log.info("Seeded demo Wallet for user {}", user.getId());
    }

    private void validateIfPresent(String setting, String publicKey) {
        if (!StringUtils.hasText(publicKey)) {
            return;
        }
        BigInteger decoded = BigInteger.ZERO;
        for (int i = 0; i < publicKey.length(); i++) {
            int digit = BASE58.indexOf(publicKey.charAt(i));
            if (digit < 0) {
                throw new IllegalStateException(setting + " must be a Base58 Solana public key");
            }
            decoded = decoded.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
        }
        int leadingZeroes = 0;
        while (leadingZeroes < publicKey.length() && publicKey.charAt(leadingZeroes) == '1') {
            leadingZeroes++;
        }
        byte[] bytes = decoded.signum() == 0 ? new byte[0] : decoded.toByteArray();
        int signByte = bytes.length > 0 && bytes[0] == 0 ? 1 : 0;
        if (leadingZeroes + bytes.length - signByte != 32) {
            throw new IllegalStateException(setting + " must decode to exactly 32 bytes");
        }
    }
}
