package com.marketplace.backend.configuration;

import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.repository.WalletRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DemoWalletSeederTest {
    private static final String CLIENT_KEY = "11111111111111111111111111111111";
    private static final String FREELANCER_KEY = "11111111111111111111111111111112";

    @Test
    void createsBothMappingsOnlyOnceAcrossRestarts() {
        WalletRepository wallets = mock(WalletRepository.class);
        Map<UUID, Wallet> stored = new HashMap<>();
        when(wallets.findFirstByUserIdOrderByIdAsc(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(wallets.save(any(Wallet.class))).thenAnswer(invocation -> {
            Wallet wallet = invocation.getArgument(0);
            stored.put(wallet.getUserId(), wallet);
            return wallet;
        });
        DemoWalletSeeder seeder = new DemoWalletSeeder(wallets, configured());
        User client = user();
        User freelancer = user();

        seeder.seed(client, freelancer);
        seeder.seed(client, freelancer);

        assertThat(stored.get(client.getId()).getPublicKey()).isEqualTo(CLIENT_KEY);
        assertThat(stored.get(freelancer.getId()).getPublicKey()).isEqualTo(FREELANCER_KEY);
        assertThat(stored).hasSize(2);
        verify(wallets, times(2)).save(any(Wallet.class));
    }

    @Test
    void absentConfigurationSkipsWalletSeed() {
        WalletRepository wallets = mock(WalletRepository.class);
        new DemoWalletSeeder(wallets, new DemoWalletProperties()).seed(user(), user());
        verifyNoInteractions(wallets);
    }

    @Test
    void invalidPublicKeyFailsBeforeWritingEitherWallet() {
        WalletRepository wallets = mock(WalletRepository.class);
        DemoWalletProperties properties = configured();
        properties.setFreelancerPublicKey("not-a-solana-key");

        assertThatThrownBy(() -> new DemoWalletSeeder(wallets, properties).seed(user(), user()))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(wallets);
    }

    @Test
    void rejectsFreelancerKeyAlreadyOwnedByClient() {
        WalletRepository wallets = mock(WalletRepository.class);
        User client = user();
        User freelancer = user();
        Wallet clientWallet = new Wallet();
        clientWallet.setUserId(client.getId());
        clientWallet.setPublicKey(CLIENT_KEY);
        Map<UUID, Wallet> stored = new HashMap<>();
        stored.put(client.getId(), clientWallet);
        when(wallets.findFirstByUserIdOrderByIdAsc(any(UUID.class)))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(wallets.existsByPublicKey(CLIENT_KEY)).thenAnswer(invocation ->
                stored.values().stream().anyMatch(wallet -> CLIENT_KEY.equals(wallet.getPublicKey())));
        DemoWalletProperties properties = new DemoWalletProperties();
        properties.setFreelancerPublicKey(CLIENT_KEY);

        assertThatThrownBy(() -> new DemoWalletSeeder(wallets, properties).seed(client, freelancer))
                .isInstanceOf(IllegalStateException.class);

        verify(wallets, never()).save(any(Wallet.class));
        assertThat(stored).containsOnlyKeys(client.getId());
        assertThat(stored.get(client.getId())).isSameAs(clientWallet);
        assertThat(clientWallet.getPublicKey()).isEqualTo(CLIENT_KEY);
    }

    private DemoWalletProperties configured() {
        DemoWalletProperties properties = new DemoWalletProperties();
        properties.setClientPublicKey(CLIENT_KEY);
        properties.setFreelancerPublicKey(FREELANCER_KEY);
        return properties;
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }
}
