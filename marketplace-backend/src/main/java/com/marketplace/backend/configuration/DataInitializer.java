package com.marketplace.backend.configuration;

import com.marketplace.backend.entity.AcceptanceCriterion;
import com.marketplace.backend.entity.AuthProvider;
import com.marketplace.backend.entity.BankCode;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.JobStatus;
import com.marketplace.backend.entity.DeliverableRequirement;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.UserType;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.AcceptanceCriterionRepository;
import com.marketplace.backend.repository.DeliverableRequirementRepository;
import com.marketplace.backend.repository.RoleRepository;
import com.marketplace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration
@Slf4j(topic = "INIT-APPLICATION")
public class DataInitializer {

    private static final String SEED_CLIENT_EMAIL = "nguyenhuutrong11133@gmail.com";
    private static final String SEED_FREELANCER_EMAIL = "freelancer.seed@example.com";
    private static final String SEED_ADMIN_EMAIL = "admin.e2e@example.test";
    private static final BankCode SEED_FREELANCER_BANK_CODE = BankCode.BIDV;
    private static final String SEED_FREELANCER_TAX_CODE = "DEMO-TAX-000001";
    private static final String SEED_FREELANCER_IDENTITY_NUMBER = "DEMO-ID-000001";
    private static final String SEED_FREELANCER_NATIONALITY = "VN";
    private static final String SEED_FREELANCER_TAX_ADDRESS = "Demo address - not a real residence";
    private static final String SEED_FREELANCER_BANK_ACCOUNT = "0000000000";
    private static final String SEED_FREELANCER_BANK_HOLDER = "DEMO FREELANCER";

    @Bean
    public ApplicationRunner initData(RoleRepository roleRepository,
                                      UserRepository userRepository,
                                      JobRepository jobRepository,
                                      AcceptanceCriterionRepository acceptanceCriterionRepository,
                                      DeliverableRequirementRepository deliverableRequirementRepository,
                                      DemoWalletSeeder demoWalletSeeder,
                                      PasswordEncoder passwordEncoder,
                                      Environment environment,
                                      @Value("${DEMO_CLIENT_PASSWORD:}") String clientPassword,
                                      @Value("${DEMO_FREELANCER_PASSWORD:}") String freelancerPassword,
                                      @Value("${DEMO_ADMIN_PASSWORD:}") String adminPassword) {
        return args -> {
            requireStrongSeedPassword("DEMO_CLIENT_PASSWORD", clientPassword);
            requireStrongSeedPassword("DEMO_FREELANCER_PASSWORD", freelancerPassword);
            if (clientPassword.equals(freelancerPassword)) {
                throw new IllegalStateException("Demo account passwords must be distinct");
            }
            if (roleRepository.count() == 0) {
                roleRepository.saveAll(List.of(
                        createRole("ROLE_USER"),
                        createRole("ROLE_ADMIN")
                ));
                log.info("Initial roles inserted");
            }
            // Existing databases may have ROLE_USER but predate the Admin role.
            if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
                roleRepository.save(createRole("ROLE_ADMIN"));
            }

            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new IllegalStateException("ROLE_USER not found after seeding"));

            if (adminPassword != null && !adminPassword.isBlank()) {
                if (!environment.acceptsProfiles(Profiles.of("dev"))
                        || environment.acceptsProfiles(Profiles.of("prod"))) {
                    throw new IllegalStateException("DEMO_ADMIN_PASSWORD is allowed only in the dev profile");
                }
                requireStrongSeedPassword("DEMO_ADMIN_PASSWORD", adminPassword);
                if (adminPassword.equals(clientPassword) || adminPassword.equals(freelancerPassword)) {
                    throw new IllegalStateException("Demo Admin password must be distinct");
                }
                Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
                User admin = userRepository.findByEmail(SEED_ADMIN_EMAIL).orElseGet(() -> {
                    User u = new User();
                    u.setEmail(SEED_ADMIN_EMAIL);
                    u.setPassword(passwordEncoder.encode(adminPassword));
                    u.setDisplayName("E2E Demo Admin");
                    u.setAuthProvider(AuthProvider.LOCAL);
                    u.setEnabled(true);
                    u.setRoles(Set.of(userRole, adminRole));
                    u.setUserType(UserType.CLIENT);
                    return userRepository.save(u);
                });
                if (admin.getRoles().stream()
                        .noneMatch(role -> "ROLE_ADMIN".equals(role.getName()))) {
                    throw new IllegalStateException("Reserved E2E Admin account has incompatible state");
                }
                if (!admin.isEnabled()) {
                    admin.setEnabled(true);
                    userRepository.save(admin);
                }
                rotateSeedPassword(admin, adminPassword, userRepository, passwordEncoder);
            } else {
                userRepository.findByEmail(SEED_ADMIN_EMAIL).ifPresent(admin -> {
                    if (admin.isEnabled() && admin.getRoles() != null && admin.getRoles().stream()
                            .anyMatch(role -> "ROLE_ADMIN".equals(role.getName()))) {
                        admin.setEnabled(false);
                        admin.setRefreshToken(null);
                        userRepository.save(admin);
                    }
                });
            }

            User client = userRepository.findByEmail(SEED_CLIENT_EMAIL).orElseGet(() -> {
                User u = new User();
                u.setEmail(SEED_CLIENT_EMAIL);
                u.setPassword(passwordEncoder.encode(clientPassword));
                u.setDisplayName("Nguyen Huu Trong");
                u.setAuthProvider(AuthProvider.LOCAL);
                u.setEnabled(true);
                u.setRoles(Set.of(userRole));
                u.setUserType(UserType.CLIENT);
                User saved = userRepository.save(u);
                log.info("Seed client created: {}", SEED_CLIENT_EMAIL);
                return saved;
            });

            rotateSeedPassword(client, clientPassword, userRepository, passwordEncoder);

            User freelancer = userRepository.findByEmail(SEED_FREELANCER_EMAIL).orElseGet(() -> {
                User u = new User();
                u.setEmail(SEED_FREELANCER_EMAIL);
                u.setPassword(passwordEncoder.encode(freelancerPassword));
                u.setDisplayName("Freelancer Seed");
                u.setAuthProvider(AuthProvider.LOCAL);
                u.setEnabled(true);
                u.setRoles(Set.of(userRole));
                u.setUserType(UserType.FREELANCER);
                u.setTaxCode(SEED_FREELANCER_TAX_CODE);
                u.setIdentityNumber(SEED_FREELANCER_IDENTITY_NUMBER);
                u.setNationality(SEED_FREELANCER_NATIONALITY);
                u.setTaxAddress(SEED_FREELANCER_TAX_ADDRESS);
                u.setBankCode(SEED_FREELANCER_BANK_CODE);
                u.setBankAccountNumber(SEED_FREELANCER_BANK_ACCOUNT);
                u.setBankAccountHolderName(SEED_FREELANCER_BANK_HOLDER);
                User saved = userRepository.save(u);
                log.info("Seed freelancer created (chua nhan job nao): {}", SEED_FREELANCER_EMAIL);
                return saved;
            });

            rotateSeedPassword(freelancer, freelancerPassword, userRepository, passwordEncoder);
            fillMissingFreelancerDemoIdentity(freelancer, userRepository);
            demoWalletSeeder.seed(client, freelancer);

            List<Job> clientJobs = jobRepository.findByClientUserId(client.getId());
            if (clientJobs.isEmpty()) {
                clientJobs = jobRepository.saveAll(List.of(
                        createOpenJob(client.getId(), "Landing page redesign",
                                "Redesign trang landing page, mobile-first.", new BigDecimal("500")),
                        createOpenJob(client.getId(), "Viet REST API cho module giao dich",
                                "Xay dung CRUD + validation cho module giao dich, tich hop don vi tien te.", new BigDecimal("750")),
                        createOpenJob(client.getId(), "Toi uu SEO trang chu",
                                "Audit va toi uu SEO on-page cho trang chu va 5 landing page chinh.", new BigDecimal("300"))
                ));
                log.info("Seed 3 job OPEN cho client {} -- chua funding va chua gan freelancer",
                        SEED_CLIENT_EMAIL);
                log.info("Freelancer seed {} (id={}) dang co 0 job -- goi POST /jobs/{{jobId}}/assignments " +
                                "voi freelancerId nay de gan thu 1 trong 3 job tren.",
                        SEED_FREELANCER_EMAIL, freelancer.getId());
                log.info("Freelancer seed {} has demo tax identity for external MISA registration on payout", freelancer.getId());
            }
            for (Job job : clientJobs) {
                backfillJobRules(job, jobRepository, acceptanceCriterionRepository, deliverableRequirementRepository);
            }
        };
    }

    private void requireStrongSeedPassword(String name, String password) {
        if (password == null || password.length() < 24) {
            throw new IllegalStateException(name + " must be supplied at runtime with at least 24 characters");
        }
    }

    private void rotateSeedPassword(User user, String password, UserRepository userRepository,
                                    PasswordEncoder passwordEncoder) {
        if (!passwordEncoder.matches(password, user.getPassword())) {
            user.setPassword(passwordEncoder.encode(password));
            user.setRefreshToken(null);
            userRepository.save(user);
        }
    }

    private void fillMissingFreelancerDemoIdentity(User user, UserRepository userRepository) {
        boolean changed = false;
        if (user.getTaxCode() == null || user.getTaxCode().isBlank()) {
            user.setTaxCode(SEED_FREELANCER_TAX_CODE);
            changed = true;
        }
        if (user.getIdentityNumber() == null || user.getIdentityNumber().isBlank()) {
            user.setIdentityNumber(SEED_FREELANCER_IDENTITY_NUMBER);
            changed = true;
        }
        if (user.getNationality() == null || user.getNationality().isBlank()) {
            user.setNationality(SEED_FREELANCER_NATIONALITY);
            changed = true;
        }
        if (user.getTaxAddress() == null || user.getTaxAddress().isBlank()) {
            user.setTaxAddress(SEED_FREELANCER_TAX_ADDRESS);
            changed = true;
        }
        if (user.getBankCode() == null) {
            user.setBankCode(SEED_FREELANCER_BANK_CODE);
            changed = true;
        }
        if (user.getBankAccountNumber() == null || user.getBankAccountNumber().isBlank()) {
            user.setBankAccountNumber(SEED_FREELANCER_BANK_ACCOUNT);
            changed = true;
        }
        if (user.getBankAccountHolderName() == null || user.getBankAccountHolderName().isBlank()) {
            user.setBankAccountHolderName(SEED_FREELANCER_BANK_HOLDER);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }
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
        job.setDeliveryDueAt(Instant.now().plus(14, ChronoUnit.DAYS));
        job.setReviewWindowHours(72);
        job.setMaxRevisions(2);
        return job;
    }

    private void backfillJobRules(Job job, JobRepository jobRepository,
                                  AcceptanceCriterionRepository acceptanceCriterionRepository,
                                  DeliverableRequirementRepository deliverableRequirementRepository) {
        boolean changed = false;
        if (job.getDeliveryDueAt() == null) {
            job.setDeliveryDueAt(Instant.now().plus(14, ChronoUnit.DAYS));
            changed = true;
        }
        if (job.getReviewWindowHours() < 24) {
            job.setReviewWindowHours(72);
            changed = true;
        }
        if (job.getMaxRevisions() < 1) {
            job.setMaxRevisions(2);
            changed = true;
        }
        if (changed) jobRepository.save(job);

        if (deliverableRequirementRepository.findByJobIdOrderByOrderAsc(job.getId()).isEmpty()) {
            DeliverableRequirement deliverable = new DeliverableRequirement();
            deliverable.setJobId(job.getId());
            deliverable.setOrder(0);
            deliverable.setTitle("Sản phẩm hoàn chỉnh");
            deliverable.setDescription(job.getDescription() != null ? job.getDescription() : job.getTitle());
            deliverable.setRequired(true);
            deliverableRequirementRepository.save(deliverable);
        }
        if (acceptanceCriterionRepository.findByJobIdOrderByOrderAsc(job.getId()).isEmpty()) {
            AcceptanceCriterion criterion = new AcceptanceCriterion();
            criterion.setJobId(job.getId());
            criterion.setOrder(0);
            criterion.setDescription("Sản phẩm đáp ứng đầy đủ phạm vi công việc đã mô tả.");
            criterion.setRequired(true);
            acceptanceCriterionRepository.save(criterion);
        }
    }
}
