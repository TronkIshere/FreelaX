package com.misa.backend.configuration;

import com.misa.backend.entity.AuthProvider;
import com.misa.backend.entity.Role;
import com.misa.backend.entity.User;
import com.misa.backend.repository.RoleRepository;
import com.misa.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

@Configuration
@Slf4j(topic = "INIT-APPLICATION")
public class DataInitializer {

    private static final String ROLE_PLATFORM_INTEGRATION = "ROLE_PLATFORM_INTEGRATION";

    @Bean
    public ApplicationRunner initData(RoleRepository roleRepository,
                                      UserRepository userRepository,
                                      PasswordEncoder passwordEncoder,
                                      @Value("${MISA_PLATFORM_ACCOUNT_EMAIL:}") String platformEmail,
                                      @Value("${MISA_PLATFORM_ACCOUNT_PASSWORD:}") String platformPassword) {
        return args -> {
            if (platformEmail == null || platformEmail.isBlank()
                    || platformPassword == null || platformPassword.length() < 24) {
                throw new IllegalStateException("MISA platform account credentials must be supplied at runtime");
            }
            if (roleRepository.count() == 0) {
                roleRepository.saveAll(List.of(
                        createRole("ROLE_USER"),
                        createRole("ROLE_ADMIN")
                ));
                log.info("Initial roles inserted");
            }

            Role platformRole = roleRepository.findByName(ROLE_PLATFORM_INTEGRATION)
                    .orElseGet(() -> roleRepository.save(createRole(ROLE_PLATFORM_INTEGRATION)));

            User platformUser = userRepository.findByEmail(platformEmail).orElseGet(() -> {
                User user = new User();
                user.setEmail(platformEmail);
                user.setPassword(passwordEncoder.encode(platformPassword));
                user.setDisplayName("Marketplace Backend (B2B integration account)");
                user.setAuthProvider(AuthProvider.LOCAL);
                user.setEnabled(true);
                user.setRoles(Set.of(platformRole));
                User saved = userRepository.save(user);
                log.info("Seed platform account created for marketplace-backend integration: {}", platformEmail);
                return saved;
            });
            if (!passwordEncoder.matches(platformPassword, platformUser.getPassword())) {
                platformUser.setPassword(passwordEncoder.encode(platformPassword));
                platformUser.setRefreshToken(null);
                userRepository.save(platformUser);
            }
        };
    }

    private Role createRole(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
