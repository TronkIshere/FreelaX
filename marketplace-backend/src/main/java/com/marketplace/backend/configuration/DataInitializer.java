package com.marketplace.backend.configuration;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.response.bofa.CheckoutOrderResult;
import com.marketplace.backend.entity.AuthProvider;
import com.marketplace.backend.entity.BankCode;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.JobStatus;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.UserType;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.RoleRepository;
import com.marketplace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Configuration
@Slf4j(topic = "INIT-APPLICATION")
public class DataInitializer {

    private static final String SEED_CLIENT_EMAIL = "nguyenhuutrong11133@gmail.com";
    private static final String SEED_CLIENT_PASSWORD = "123456789";
    private static final String SEED_CLIENT_BANK_CODE = "BANK_OF_AMERICA";
    private static final String SEED_CLIENT_BANK_ACCOUNT_NUMBER = "483920175610";
    private static final String SEED_CLIENT_BANK_ACCOUNT_HOLDER_NAME = "NGUYEN HUU TRONG";

    private static final String SEED_FREELANCER_EMAIL = "freelancer.seed@example.com";
    private static final String SEED_FREELANCER_PASSWORD = "123456789";
    private static final BankCode SEED_FREELANCER_BANK_CODE = BankCode.BIDV;

    @Bean
    public ApplicationRunner initData(RoleRepository roleRepository,
                                      UserRepository userRepository,
                                      JobRepository jobRepository,
                                      PaymentBackendClient paymentBackendClient,
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

            User client = userRepository.findByEmail(SEED_CLIENT_EMAIL).orElseGet(() -> {
                User u = new User();
                u.setEmail(SEED_CLIENT_EMAIL);
                u.setPassword(passwordEncoder.encode(SEED_CLIENT_PASSWORD));
                u.setDisplayName("Nguyen Huu Trong");
                u.setAuthProvider(AuthProvider.LOCAL);
                u.setEnabled(true);
                u.setRoles(Set.of(userRole));
                u.setUserType(UserType.CLIENT);
                User saved = userRepository.save(u);
                log.info("Seed client created: {}", SEED_CLIENT_EMAIL);
                return saved;
            });

            User freelancer = userRepository.findByEmail(SEED_FREELANCER_EMAIL).orElseGet(() -> {
                User u = new User();
                u.setEmail(SEED_FREELANCER_EMAIL);
                u.setPassword(passwordEncoder.encode(SEED_FREELANCER_PASSWORD));
                u.setDisplayName("Freelancer Seed");
                u.setAuthProvider(AuthProvider.LOCAL);
                u.setEnabled(true);
                u.setRoles(Set.of(userRole));
                u.setUserType(UserType.FREELANCER);
                u.setBankCode(SEED_FREELANCER_BANK_CODE);
                u.setBankAccountNumber(generateRandomBankAccountNumber());
                u.setBankAccountHolderName("Freelancer Seed");
                User saved = userRepository.save(u);
                log.info("Seed freelancer created (chua nhan job nao): {} -- bankCode={}, bankAccountNumber={}",
                        SEED_FREELANCER_EMAIL, SEED_FREELANCER_BANK_CODE, saved.getBankAccountNumber());
                return saved;
            });

            if (jobRepository.findByClientUserId(client.getId()).isEmpty()) {
                List<Job> seedJobs = jobRepository.saveAll(List.of(
                        createOpenJob(client.getId(), "Landing page redesign",
                                "Redesign trang landing page, mobile-first.", new BigDecimal("500")),
                        createOpenJob(client.getId(), "Viet REST API cho module giao dich",
                                "Xay dung CRUD + validation cho module giao dich, tich hop don vi tien te.", new BigDecimal("750")),
                        createOpenJob(client.getId(), "Toi uu SEO trang chu",
                                "Audit va toi uu SEO on-page cho trang chu va 5 landing page chinh.", new BigDecimal("300"))
                ));

                for (Job job : seedJobs) {
                    CheckoutOrderResult checkoutOrder = paymentBackendClient.createCheckoutOrder(
                            client.getId(),
                            job.getId(),
                            job.getBudgetUsd(),
                            SEED_CLIENT_BANK_CODE,
                            SEED_CLIENT_BANK_ACCOUNT_NUMBER,
                            SEED_CLIENT_BANK_ACCOUNT_HOLDER_NAME
                    );
                    job.setCheckoutOrderId(checkoutOrder.getId());
                    job.setPayerBankCode(SEED_CLIENT_BANK_CODE);
                    job.setPayerBankAccountNumber(SEED_CLIENT_BANK_ACCOUNT_NUMBER);
                    job.setPayerBankAccountHolderName(SEED_CLIENT_BANK_ACCOUNT_HOLDER_NAME);
                    jobRepository.save(job);
                }

                log.info("Seed 3 job OPEN cho client {} -- CHUA gan freelancer nao ca, da tao checkout order tren payment-backend",
                        SEED_CLIENT_EMAIL);
                log.info("Freelancer seed {} (id={}) dang co 0 job -- goi PATCH /jobs/{{jobId}}/assign-freelancer " +
                                "voi freelancerId nay de gan thu 1 trong 3 job tren.",
                        SEED_FREELANCER_EMAIL, freelancer.getId());
                log.warn("Freelancer seed {} chua co misaTaxpayerId (seed tao truc tiep, khong qua registerUser() " +
                                "nen khong tu goi misa-backend) -- job cua freelancer nay van thanh toan binh thuong, " +
                                "chi buoc xuat chung tu thue tu dong se bi SKIPPED_NO_TAXPAYER.",
                        freelancer.getId());
            }
        };
    }

    private String generateRandomBankAccountNumber() {
        long number = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
        return String.valueOf(number);
    }

    private Role createRole(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    private Job createOpenJob(UUID clientUserId, String title, String description, BigDecimal budgetUsd) {
        Job job = new Job();
        job.setClientUserId(clientUserId);
        job.setTitle(title);
        job.setDescription(description);
        job.setBudgetUsd(budgetUsd);
        job.setStatus(JobStatus.OPEN);
        return job;
    }
}