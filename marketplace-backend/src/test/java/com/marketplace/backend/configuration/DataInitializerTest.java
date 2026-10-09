package com.marketplace.backend.configuration;

import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.UserType;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.AcceptanceCriterionRepository;
import com.marketplace.backend.repository.DeliverableRequirementRepository;
import com.marketplace.backend.repository.RoleRepository;
import com.marketplace.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataInitializerTest {
    @Test
    void rejectsMissingOrWeakRuntimePasswordsBeforeSeeding() {
        DataInitializer initializer = new DataInitializer();
        assertThatThrownBy(() -> initializer.initData(null, null, null, null, null, null, null,
                null, "short", "f".repeat(32), "").run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_CLIENT_PASSWORD");
    }

    @Test
    void rotatesExistingDemoPasswordsAndRevokesRefreshSessions() throws Exception {
        RoleRepository roles = mock(RoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        AcceptanceCriterionRepository criteria = mock(AcceptanceCriterionRepository.class);
        DeliverableRequirementRepository deliverables = mock(DeliverableRequirementRepository.class);
        DemoWalletSeeder wallets = mock(DemoWalletSeeder.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Role role = new Role();
        role.setName("ROLE_USER");
        User client = new User();
        client.setId(UUID.randomUUID());
        client.setPassword("old-client-hash");
        client.setRefreshToken("old-client-refresh");
        User freelancer = new User();
        freelancer.setId(UUID.randomUUID());
        freelancer.setPassword("old-freelancer-hash");
        freelancer.setRefreshToken("old-freelancer-refresh");

        when(roles.count()).thenReturn(1L);
        when(roles.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(users.findByEmail(any())).thenAnswer(invocation ->
                Optional.of(invocation.getArgument(0).equals("freelancer.seed@example.com") ? freelancer : client));
        when(encoder.matches(any(), any())).thenReturn(false);
        when(encoder.encode(any())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
        when(jobs.findByClientUserId(any())).thenReturn(List.of(new Job()));

        new DataInitializer().initData(roles, users, jobs, criteria, deliverables, wallets, encoder,
                null, "c".repeat(32), "f".repeat(32), "")
                .run(new DefaultApplicationArguments(new String[0]));

        assertThat(client.getPassword()).isEqualTo("hash:" + "c".repeat(32));
        assertThat(freelancer.getPassword()).isEqualTo("hash:" + "f".repeat(32));
        assertThat(client.getRefreshToken()).isNull();
        assertThat(freelancer.getRefreshToken()).isNull();
        verify(users).save(client);
        verify(users, org.mockito.Mockito.atLeastOnce()).save(freelancer);
    }

    @Test
    void seededFreelancerHasRequiredExternalTaxpayerAndBankIdentity() throws Exception {
        RoleRepository roles = mock(RoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        JobRepository jobs = mock(JobRepository.class);
        AcceptanceCriterionRepository criteria = mock(AcceptanceCriterionRepository.class);
        DeliverableRequirementRepository deliverables = mock(DeliverableRequirementRepository.class);
        DemoWalletSeeder wallets = mock(DemoWalletSeeder.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Role role = new Role();
        role.setName("ROLE_USER");
        when(roles.count()).thenReturn(1L);
        when(roles.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(jobs.findByClientUserId(any())).thenReturn(List.of(new Job()));

        new DataInitializer().initData(roles, users, jobs, criteria, deliverables, wallets, encoder,
                null, "c".repeat(32), "f".repeat(32), "")
                .run(new DefaultApplicationArguments(new String[0]));

        User freelancer = org.mockito.Mockito.mockingDetails(users).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (User) invocation.getArgument(0))
                .filter(user -> user.getUserType() == UserType.FREELANCER)
                .findFirst().orElseThrow();
        assertThat(freelancer.getTaxCode()).startsWith("DEMO-");
        assertThat(freelancer.getIdentityNumber()).startsWith("DEMO-");
        assertThat(freelancer.getNationality()).isNotBlank();
        assertThat(freelancer.getTaxAddress()).isNotBlank();
        assertThat(freelancer.getBankCode()).isNotNull();
        assertThat(freelancer.getBankAccountNumber()).isNotBlank();
        assertThat(freelancer.getBankAccountHolderName()).isNotBlank();
        verify(wallets).seed(any(User.class), any(User.class));
    }
}
