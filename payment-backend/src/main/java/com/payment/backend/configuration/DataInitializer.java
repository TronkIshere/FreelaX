package com.payment.backend.configuration;

import com.payment.backend.entity.AuthProvider;
import com.payment.backend.entity.BofaCheckoutOrder;
import com.payment.backend.entity.BofaCheckoutOrderStatus;
import com.payment.backend.entity.Role;
import com.payment.backend.entity.User;
import com.payment.backend.repository.BofaCheckoutOrderRepository;
import com.payment.backend.repository.RoleRepository;
import com.payment.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration
@Slf4j(topic = "INIT-APPLICATION")
public class DataInitializer {

    private static final String DEMO_BANK_CODE = "BANK_OF_AMERICA";
    private static final String DEMO_BANK_ACCOUNT_NUMBER = "483920175610";
    private static final String DEMO_BANK_ACCOUNT_HOLDER_NAME = "NGUYEN HUU TRONG";
    private static final BigDecimal DEMO_AMOUNT_USD = new BigDecimal("500");

    private static final String SEED_USER_EMAIL = "test@example.com";
    private static final String SEED_USER_DISPLAY_NAME = "Test User";

    @Bean
    public ApplicationRunner initData(RoleRepository roleRepository,
                                      UserRepository userRepository,
                                      BofaCheckoutOrderRepository bofaCheckoutOrderRepository,
                                      PasswordEncoder passwordEncoder,
                                      @Value("${PAYMENT_DEMO_USER_PASSWORD:}") String demoPassword) {
        return args -> {
            if (demoPassword == null || demoPassword.length() < 24) {
                throw new IllegalStateException("PAYMENT_DEMO_USER_PASSWORD must be supplied at runtime with at least 24 characters");
            }
            if (roleRepository.count() == 0) {
                roleRepository.saveAll(List.of(
                        createRole("ROLE_USER"),
                        createRole("ROLE_ADMIN")
                ));
                log.info("Initial roles inserted");
            }

            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new IllegalStateException("ROLE_USER not found after seeding"));

            User seedUser = userRepository.findByEmail(SEED_USER_EMAIL).orElseGet(() -> {
                User user = new User();
                user.setEmail(SEED_USER_EMAIL);
                user.setPassword(passwordEncoder.encode(demoPassword));
                user.setDisplayName(SEED_USER_DISPLAY_NAME);
                user.setAuthProvider(AuthProvider.LOCAL);
                user.setEnabled(true);
                user.setRoles(Set.of(userRole));
                User saved = userRepository.save(user);
                log.info("Seed user created: {}", SEED_USER_EMAIL);
                return saved;
            });
            if (!passwordEncoder.matches(demoPassword, seedUser.getPassword())) {
                seedUser.setPassword(passwordEncoder.encode(demoPassword));
                seedUser.setRefreshToken(null);
                userRepository.save(seedUser);
            }

            if (bofaCheckoutOrderRepository.count() == 0) {
                BofaCheckoutOrder demo = new BofaCheckoutOrder();
                demo.setPayerUserId(UUID.randomUUID());
                demo.setJobId(UUID.randomUUID());
                demo.setAmountUsd(DEMO_AMOUNT_USD);
                demo.setBofaOrderId(UUID.randomUUID().toString());
                demo.setPayerBankCode(DEMO_BANK_CODE);
                demo.setPayerBankAccountNumber(DEMO_BANK_ACCOUNT_NUMBER);
                demo.setPayerBankAccountHolderName(DEMO_BANK_ACCOUNT_HOLDER_NAME);
                demo.setStatus(BofaCheckoutOrderStatus.CREATED);
                demo.setCreatedAt(LocalDateTime.now());
                bofaCheckoutOrderRepository.save(demo);
                log.info("Seed 1 BofaCheckoutOrder demo (id={}, status=CREATED) -- goi POST /internal/BofA/checkout/orders/{}/capture de test truc tiep",
                        demo.getId(), demo.getId());
            }
        };
    }

    private Role createRole(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
