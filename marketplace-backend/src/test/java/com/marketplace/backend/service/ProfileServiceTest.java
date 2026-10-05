package com.marketplace.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProfileServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private UserRepository users;
    private MemberProfileRepository profiles;
    private PortfolioItemRepository portfolio;
    private WorkContractRepository contracts;
    private FundingTransactionRepository funding;
    private ProfileService service;
    private User freelancer;
    private MemberProfile stored;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class); profiles = mock(MemberProfileRepository.class);
        portfolio = mock(PortfolioItemRepository.class); contracts = mock(WorkContractRepository.class);
        funding = mock(FundingTransactionRepository.class);
        service = new ProfileService(users, profiles, portfolio, contracts, funding, mapper);
        freelancer = new User(); freelancer.setId(UUID.randomUUID()); freelancer.setEnabled(true);
        freelancer.setUserType(UserType.FREELANCER); freelancer.setDisplayName("Freelancer");
        freelancer.setEmail("private@example.test"); freelancer.setTaxCode("private-tax");
        freelancer.setBankAccountNumber("private-bank");
        when(users.findById(freelancer.getId())).thenReturn(Optional.of(freelancer));
        when(users.findWithLockById(freelancer.getId())).thenReturn(Optional.of(freelancer));
        when(profiles.findByUserId(freelancer.getId())).thenAnswer(x -> Optional.ofNullable(stored));
        when(profiles.saveAndFlush(any())).thenAnswer(x -> {
            stored = x.getArgument(0); stored.setId(UUID.randomUUID()); return stored;
        });
    }

    @Test
    void publicProfileHasNoPrivateIdentityOrFinancialFieldsAndNoFakeRating() throws Exception {
        when(contracts.countByFreelancerIdAndStatus(freelancer.getId(), ContractStatus.COMPLETED)).thenReturn(3L);
        when(contracts.countFreelancerDisputes(freelancer.getId())).thenReturn(1L);
        var publicView = service.publicProfile(freelancer.getId());
        String json = mapper.writeValueAsString(publicView);
        assertThat(json).contains("\"completedContracts\":3", "\"disputeCount\":1", "\"reviewCount\":0");
        assertThat(json).doesNotContain("private@example.test", "private-tax", "private-bank");
        assertThat(mapper.readTree(json).has("email")).isFalse();
        assertThat(publicView.reputation().averageRating()).isNull();
        assertThat(publicView.reputation().onTimeRate()).isNull();
        assertThat(service.me(freelancer.getId()).email()).isEqualTo("private@example.test");
    }

    @Test
    void ownerCanUpdateVersionedProfileAndStaleUpdateFails() throws Exception {
        ObjectNode request = object("{\"version\":0,\"displayName\":\"New Name\",\"avatarUrl\":\"https://example.test/a.png\",\"countryCode\":\"VN\"}");
        var result = service.patch(freelancer.getId(), request);
        assertThat(result.displayName()).isEqualTo("New Name");
        assertThat(result.avatarUrl()).isEqualTo("https://example.test/a.png");
        assertThat(result.countryCode()).isEqualTo("VN");
        stored.setVersion(1);
        assertCode(ErrorCode.PROFILE_STALE, () -> service.patch(freelancer.getId(), request));
    }

    @Test
    void profileRejectsUnsafeUrlWrongRoleAndUnownedAccount() throws Exception {
        assertCode(ErrorCode.PROFILE_INVALID, () -> service.patch(freelancer.getId(),
                object("{\"version\":0,\"avatarUrl\":\"http://example.test/a.png\"}")));
        assertCode(ErrorCode.PROFILE_INVALID, () -> service.patch(freelancer.getId(),
                object("{\"version\":0,\"companyName\":\"Corp\"}")));
        assertCode(ErrorCode.ACCOUNT_NOT_FOUND, () -> service.patch(UUID.randomUUID(),
                object("{\"version\":0,\"headline\":\"Hello\"}")));
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    void skillsReplaceRequiresFreelancerAndDeduplicatedBoundedArray() throws Exception {
        var view = service.replaceSkills(freelancer.getId(),
                object("{\"version\":0,\"skills\":[\"Java\",\"Spring\"]}"));
        assertThat(view.skills()).containsExactly("Java", "Spring");
        assertCode(ErrorCode.PROFILE_INVALID, () -> service.replaceSkills(freelancer.getId(),
                object("{\"version\":0,\"skills\":[\"Java\",\"java\"]}")));
        freelancer.setUserType(UserType.CLIENT);
        assertCode(ErrorCode.PROFILE_INVALID, () -> service.replaceSkills(freelancer.getId(),
                object("{\"version\":0,\"skills\":[\"Java\"]}")));
    }

    @Test
    void portfolioHasOwnerLimitAndHttpsValidation() throws Exception {
        when(portfolio.countByUserId(freelancer.getId())).thenReturn(12L);
        assertCode(ErrorCode.PORTFOLIO_LIMIT, () -> service.addPortfolio(freelancer.getId(),
                object("{\"title\":\"Work\",\"description\":\"Details\"}")));
        when(portfolio.countByUserId(freelancer.getId())).thenReturn(0L);
        assertCode(ErrorCode.PROFILE_INVALID, () -> service.addPortfolio(freelancer.getId(),
                object("{\"title\":\"Work\",\"description\":\"Details\",\"projectUrl\":\"http://example.test\"}")));
        var item = new PortfolioItem(); item.setId(UUID.randomUUID()); item.setUserId(freelancer.getId());
        item.setTitle("Work"); item.setDescription("Details");
        when(portfolio.saveAndFlush(any())).thenReturn(item);
        assertThat(service.addPortfolio(freelancer.getId(),
                object("{\"title\":\"Work\",\"description\":\"Details\",\"projectUrl\":\"https://example.test\"}"))
                .id()).isEqualTo(item.getId());
    }

    @Test
    void unrelatedUserCannotEditOrDeletePortfolio() throws Exception {
        UUID id = UUID.randomUUID();
        when(portfolio.findByIdAndUserId(id, freelancer.getId())).thenReturn(Optional.empty());
        assertCode(ErrorCode.PORTFOLIO_NOT_FOUND, () -> service.patchPortfolio(freelancer.getId(), id,
                object("{\"version\":0,\"title\":\"Changed\"}")));
        assertCode(ErrorCode.PORTFOLIO_NOT_FOUND, () -> service.deletePortfolio(freelancer.getId(), id));
        verify(portfolio, never()).delete(any());
    }

    private ObjectNode object(String json) {
        try { return (ObjectNode) mapper.readTree(json); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private void assertCode(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(ApplicationException.class)
                .extracting(ex -> ((ApplicationException) ex).getErrorCode()).isEqualTo(expected);
    }
}
