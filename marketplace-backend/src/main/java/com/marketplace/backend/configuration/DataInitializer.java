package com.marketplace.backend.configuration;

import com.marketplace.backend.entity.AuthProvider;
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

@Configuration
@Slf4j(topic = "INIT-APPLICATION")
public class DataInitializer {

    private static final String SEED_CLIENT_EMAIL = "nguyenhuutrong11133@gmail.com";
    private static final String SEED_CLIENT_PASSWORD = "123456789";

    private static final String SEED_FREELANCER_EMAIL = "freelancer.seed@example.com";
    private static final String SEED_FREELANCER_PASSWORD = "123456789";
    private static final UUID SEED_FREELANCER_PLACEHOLDER_PAYPAL_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Bean
    public ApplicationRunner initData(RoleRepository roleRepository,
                                      UserRepository userRepository,
                                      JobRepository jobRepository,
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
                u.setPaypalUserId(SEED_FREELANCER_PLACEHOLDER_PAYPAL_USER_ID);
                User saved = userRepository.save(u);
                log.info("Seed freelancer created: {}", SEED_FREELANCER_EMAIL);
                return saved;
            });

            if (jobRepository.findByClientUserId(client.getId()).isEmpty()) {
                jobRepository.saveAll(List.of(
                        createJob(client.getId(), freelancer.getId(), "Landing page redesign",
                                "Redesign trang landing page, mobile-first.", new BigDecimal("500")),
                        createJob(client.getId(), freelancer.getId(), "Viet REST API cho module giao dich",
                                "Xay dung CRUD + validation cho module giao dich, tich hop don vi tien te.", new BigDecimal("750")),
                        createJob(client.getId(), freelancer.getId(), "Toi uu SEO trang chu",
                                "Audit va toi uu SEO on-page cho trang chu va 5 landing page chinh.", new BigDecimal("300"))
                ));
                log.info("Seed jobs created for client {} / freelancer {}", SEED_CLIENT_EMAIL, SEED_FREELANCER_EMAIL);
                log.warn("Seed freelancer {} co paypalUserId placeholder={} -- dang nhap tai khoan freelancer " +
                                "seed ben paypal-backend, GET /api/v1/auth/me lay userId thuc, roi UPDATE lai " +
                                "field paypalUserId cua user nay truoc khi goi /pay",
                        freelancer.getId(), SEED_FREELANCER_PLACEHOLDER_PAYPAL_USER_ID);
            }
        };
    }

    private Role createRole(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    private Job createJob(UUID clientUserId, UUID freelancerId, String title, String description, BigDecimal budgetUsd) {
        Job job = new Job();
        job.setClientUserId(clientUserId);
        job.setFreelancerId(freelancerId);
        job.setTitle(title);
        job.setDescription(description);
        job.setBudgetUsd(budgetUsd);
        job.setStatus(JobStatus.OPEN);
        return job;
    }
}