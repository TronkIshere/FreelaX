package com.paypal.backend.configuration;

import com.paypal.backend.entity.AuthProvider;
import com.paypal.backend.entity.Role;
import com.paypal.backend.entity.User;
import com.paypal.backend.repository.RoleRepository;
import com.paypal.backend.repository.UserRepository;
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

    private static final String PAYER_EMAIL = "nguyenhuutrong11133@gmail.com";
    private static final String PAYER_PASSWORD = "123456789";
    private static final String PAYER_DISPLAY_NAME = "Nguyen Huu Trong";

    private static final String RECEIVER_EMAIL = "freelancer@example.com";
    private static final String RECEIVER_PASSWORD = "123456789";
    private static final String RECEIVER_DISPLAY_NAME = "freelancer";

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

            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new IllegalStateException("ROLE_USER not found after seeding"));

            createUserIfMissing(userRepository, passwordEncoder, userRole,
                    PAYER_EMAIL, PAYER_PASSWORD, PAYER_DISPLAY_NAME);

            User receiver = createUserIfMissing(userRepository, passwordEncoder, userRole,
                    RECEIVER_EMAIL, RECEIVER_PASSWORD, RECEIVER_DISPLAY_NAME);

        };
    }

    private User createUserIfMissing(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                     Role userRole, String email, String password, String displayName) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setDisplayName(displayName);
            user.setAuthProvider(AuthProvider.LOCAL);
            user.setEnabled(true);
            user.setRoles(Set.of(userRole));
            User saved = userRepository.save(user);
            log.info("Seed user created: {}", email);
            return saved;
        });
    }

    private Role createRole(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}