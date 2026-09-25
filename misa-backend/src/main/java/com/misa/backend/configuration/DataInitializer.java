package com.misa.backend.configuration;

import com.misa.backend.entity.AuthProvider;
import com.misa.backend.entity.Role;
import com.misa.backend.entity.User;
import com.misa.backend.repository.RoleRepository;
import com.misa.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

@Configuration
@Slf4j(topic = "INIT-APPLICATION")
public class DataInitializer {

    private static final String PLATFORM_ACCOUNT_EMAIL = "platform@marketplace.local";
    private static final String PLATFORM_ACCOUNT_PASSWORD = "MisaPlatform@2026";
    private static final String ROLE_PLATFORM_INTEGRATION = "ROLE_PLATFORM_INTEGRATION";

    @Bean
    public ApplicationRunner initData(RoleRepository roleRepository,
                                      UserRepository userRepository,
                                      PasswordEncoder passwordEncoder) {
        return args -> {
            if (roleRepository.count() == 0) {
                roleRepository.saveAll(List.of(
                        createRole("ROLE_USER"),
                        createRole("ROLE_ADMIN")
                ));
                log.info("Initial roles inserted");
            }

            Role platformRole = roleRepository.findByName(ROLE_PLATFORM_INTEGRATION)
                    .orElseGet(() -> roleRepository.save(createRole(ROLE_PLATFORM_INTEGRATION)));

            if (!userRepository.existsByEmail(PLATFORM_ACCOUNT_EMAIL)) {
                User platformUser = new User();
                platformUser.setEmail(PLATFORM_ACCOUNT_EMAIL);
                platformUser.setPassword(passwordEncoder.encode(PLATFORM_ACCOUNT_PASSWORD));
                platformUser.setDisplayName("Marketplace Backend (B2B integration account)");
                platformUser.setAuthProvider(AuthProvider.LOCAL);
                platformUser.setEnabled(true);
                platformUser.setRoles(Set.of(platformRole));
                userRepository.save(platformUser);
                log.info("Seed platform account created for marketplace-backend integration: {}", PLATFORM_ACCOUNT_EMAIL);
            }
        };
    }

    private Role createRole(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}