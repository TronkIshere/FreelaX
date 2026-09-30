package com.payment.backend.configuration;

import com.payment.backend.entity.Role;
import com.payment.backend.entity.User;
import com.payment.backend.repository.BofaCheckoutOrderRepository;
import com.payment.backend.repository.RoleRepository;
import com.payment.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataInitializerTest {
    @Test
    void rejectsWeakDemoPassword() {
        assertThatThrownBy(() -> new DataInitializer().initData(null, null, null, null, "short")
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENT_DEMO_USER_PASSWORD");
    }

    @Test
    void rotatesExistingDemoPasswordAndRevokesRefreshSession() throws Exception {
        RoleRepository roles = mock(RoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        BofaCheckoutOrderRepository orders = mock(BofaCheckoutOrderRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Role role = new Role();
        role.setName("ROLE_USER");
        User existing = new User();
        existing.setPassword("old-hash");
        existing.setRefreshToken("old-refresh");
        when(roles.count()).thenReturn(1L);
        when(roles.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(users.findByEmail(any())).thenReturn(Optional.of(existing));
        when(orders.count()).thenReturn(1L);
        when(encoder.matches(any(), any())).thenReturn(false);
        when(encoder.encode(any())).thenReturn("new-hash");

        new DataInitializer().initData(roles, users, orders, encoder, "p".repeat(32))
                .run(new DefaultApplicationArguments(new String[0]));

        assertThat(existing.getPassword()).isEqualTo("new-hash");
        assertThat(existing.getRefreshToken()).isNull();
        verify(users).save(existing);
    }
}
