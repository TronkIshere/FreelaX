package com.payment.backend.configuration;

import com.payment.backend.entity.BofaCheckoutOrder;
import com.payment.backend.entity.BofaCheckoutOrderStatus;
import com.payment.backend.entity.Role;
import com.payment.backend.repository.BofaCheckoutOrderRepository;
import com.payment.backend.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Configuration
@Slf4j(topic = "INIT-APPLICATION")
public class DataInitializer {

    private static final String DEMO_BANK_CODE = "BANK_OF_AMERICA";
    private static final String DEMO_BANK_ACCOUNT_NUMBER = "483920175610";
    private static final String DEMO_BANK_ACCOUNT_HOLDER_NAME = "NGUYEN HUU TRONG";
    private static final BigDecimal DEMO_AMOUNT_USD = new BigDecimal("500");

    @Bean
    public ApplicationRunner initData(RoleRepository roleRepository,
                                      BofaCheckoutOrderRepository bofaCheckoutOrderRepository) {
        return args -> {
            if (roleRepository.count() == 0) {
                roleRepository.saveAll(List.of(
                        createRole("ROLE_USER"),
                        createRole("ROLE_ADMIN")
                ));
                log.info("Initial roles inserted");
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