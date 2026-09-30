package com.misa.backend.configuration;

import com.misa.backend.entity.Role;
import com.misa.backend.entity.User;
import com.misa.backend.repository.RoleRepository;
import com.misa.backend.repository.UserRepository;
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
    void rejectsMissingPlatformCredential() {
        assertThatThrownBy(() -> new DataInitializer().initData(null, null, null,
                "platform@marketplace.local", "short")
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MISA platform account credentials");
    }

    @Test
    void rotatesExistingPlatformPasswordAndRevokesRefreshSession() throws Exception {
        RoleRepository roles = mock(RoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Role role = new Role();
        role.setName("ROLE_PLATFORM_INTEGRATION");
        User existing = new User();
        existing.setPassword("old-hash");
        existing.setRefreshToken("old-refresh");
        when(roles.count()).thenReturn(1L);
        when(roles.findByName("ROLE_PLATFORM_INTEGRATION")).thenReturn(Optional.of(role));
        when(users.findByEmail(any())).thenReturn(Optional.of(existing));
        when(encoder.matches(any(), any())).thenReturn(false);
        when(encoder.encode(any())).thenReturn("new-hash");

        new DataInitializer().initData(roles, users, encoder,
                "platform@marketplace.local", "m".repeat(32))
                .run(new DefaultApplicationArguments(new String[0]));

        assertThat(existing.getPassword()).isEqualTo("new-hash");
        assertThat(existing.getRefreshToken()).isNull();
        verify(users).save(existing);
    }
}
