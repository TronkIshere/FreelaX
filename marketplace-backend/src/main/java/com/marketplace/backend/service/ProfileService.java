package com.marketplace.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marketplace.backend.dto.response.profile.PortfolioResponse;
import com.marketplace.backend.dto.response.profile.ProfileResponse;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private static final Set<String> PROFILE_FIELDS = Set.of("version", "displayName", "avatarUrl", "headline",
            "bio", "countryCode", "languages", "hourlyRateUsd", "availability", "companyName", "companyWebsite");
    private static final Set<String> PORTFOLIO_FIELDS = Set.of("version", "title", "description", "projectUrl",
            "thumbnailUrl", "skills", "completedAt", "sortOrder");
    private final UserRepository users;
    private final MemberProfileRepository profiles;
    private final PortfolioItemRepository portfolio;
    private final WorkContractRepository contracts;
    private final FundingTransactionRepository funding;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public ProfileResponse me(UUID actor) {
        return view(user(actor), true);
    }

    @Transactional(readOnly = true)
    public ProfileResponse publicProfile(UUID userId) {
        return view(user(userId), false);
    }

    @Transactional
    public ProfileResponse patch(UUID actor, ObjectNode input) {
        checkFields(input, PROFILE_FIELDS);
        long expected = version(input);
        User user = lockedUser(actor);
        MemberProfile row = profiles.findByUserId(actor).orElseGet(() -> blank(actor));
        if (row.getVersion() != expected) throw new ApplicationException(ErrorCode.PROFILE_STALE);
        if (input.has("displayName")) user.setDisplayName(requiredText(input.get("displayName"), 2, 80));
        if (input.has("avatarUrl")) row.setAvatarUrl(url(input.get("avatarUrl")));
        if (input.has("headline")) row.setHeadline(optionalText(input.get("headline"), 120));
        if (input.has("bio")) row.setBio(optionalText(input.get("bio"), 2000));
        if (input.has("countryCode")) row.setCountryCode(country(input.get("countryCode")));
        if (input.has("languages")) row.setLanguagesJson(json(languages(input.get("languages"))));
        if (input.has("hourlyRateUsd") || input.has("availability")) {
            if (user.getUserType() != UserType.FREELANCER) throw invalid();
            if (input.has("hourlyRateUsd")) row.setHourlyRateUsd(rate(input.get("hourlyRateUsd")));
            if (input.has("availability")) row.setAvailability(optionalText(input.get("availability"), 40));
        }
        if (input.has("companyName") || input.has("companyWebsite")) {
            if (user.getUserType() != UserType.CLIENT) throw invalid();
            if (input.has("companyName")) row.setCompanyName(optionalText(input.get("companyName"), 120));
            if (input.has("companyWebsite")) row.setCompanyWebsite(url(input.get("companyWebsite")));
        }
        row.setEditedAt(Instant.now());
        profiles.saveAndFlush(row);
        return view(user, true);
    }

    @Transactional
    public ProfileResponse replaceSkills(UUID actor, ObjectNode input) {
        checkFields(input, Set.of("version", "skills"));
        long expected = version(input);
        User user = lockedUser(actor);
        if (user.getUserType() != UserType.FREELANCER) throw invalid();
        MemberProfile row = profiles.findByUserId(actor).orElseGet(() -> blank(actor));
        if (row.getVersion() != expected || !input.has("skills")) throw new ApplicationException(ErrorCode.PROFILE_STALE);
        row.setSkillsJson(json(skills(input.get("skills"))));
        row.setEditedAt(Instant.now());
        profiles.saveAndFlush(row);
        return view(user, true);
    }

    @Transactional(readOnly = true)
    public List<PortfolioResponse> portfolio(UUID userId) {
        user(userId);
        return portfolio.findByUserIdOrderBySortOrderAscCreatedAtAsc(userId).stream().map(this::item).toList();
    }

    @Transactional
    public PortfolioResponse addPortfolio(UUID actor, ObjectNode input) {
        checkFields(input, PORTFOLIO_FIELDS);
        if (input.has("version")) throw invalid();
        freelancer(actor);
        if (portfolio.countByUserId(actor) >= 12) throw new ApplicationException(ErrorCode.PORTFOLIO_LIMIT);
        PortfolioItem row = new PortfolioItem();
        row.setUserId(actor);
        applyPortfolio(row, input, true);
        return item(portfolio.saveAndFlush(row));
    }

    @Transactional
    public PortfolioResponse patchPortfolio(UUID actor, UUID id, ObjectNode input) {
        checkFields(input, PORTFOLIO_FIELDS);
        long expected = version(input);
        freelancer(actor);
        PortfolioItem row = portfolio.findByIdAndUserId(id, actor)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PORTFOLIO_NOT_FOUND));
        if (row.getVersion() != expected) throw new ApplicationException(ErrorCode.PROFILE_STALE);
        applyPortfolio(row, input, false);
        return item(portfolio.saveAndFlush(row));
    }

    @Transactional
    public void deletePortfolio(UUID actor, UUID id) {
        freelancer(actor);
        PortfolioItem row = portfolio.findByIdAndUserId(id, actor)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PORTFOLIO_NOT_FOUND));
        portfolio.delete(row);
    }

    private void applyPortfolio(PortfolioItem row, ObjectNode input, boolean create) {
        if (create && (!input.has("title") || !input.has("description"))) throw invalid();
        if (input.has("title")) row.setTitle(requiredText(input.get("title"), 2, 160));
        if (input.has("description")) row.setDescription(requiredText(input.get("description"), 2, 2000));
        if (input.has("projectUrl")) row.setProjectUrl(url(input.get("projectUrl")));
        if (input.has("thumbnailUrl")) row.setThumbnailUrl(url(input.get("thumbnailUrl")));
        if (input.has("skills")) row.setSkillsJson(json(skills(input.get("skills"))));
        if (input.has("completedAt")) {
            JsonNode value = input.get("completedAt");
            if (value.isNull()) row.setCompletedAt(null);
            else {
                if (!value.isTextual()) throw invalid();
                try { row.setCompletedAt(LocalDate.parse(value.asText())); }
                catch (DateTimeParseException ex) { throw invalid(); }
                if (row.getCompletedAt().isAfter(LocalDate.now())) throw invalid();
            }
        }
        if (input.has("sortOrder")) {
            JsonNode value = input.get("sortOrder");
            if (!value.isIntegralNumber() || value.asInt() < 0 || value.asInt() > 1000) throw invalid();
            row.setSortOrder(value.asInt());
        }
    }

    private ProfileResponse view(User user, boolean own) {
        MemberProfile row = profiles.findByUserId(user.getId()).orElse(null);
        boolean freelancer = user.getUserType() == UserType.FREELANCER;
        long completed = freelancer
                ? contracts.countByFreelancerIdAndStatus(user.getId(), ContractStatus.COMPLETED)
                : contracts.countByClientUserIdAndStatus(user.getId(), ContractStatus.COMPLETED);
        Long funded = freelancer ? null
                : funding.countDistinctContractsByClientAndStatus(user.getId(), FundingStatus.SUCCEEDED);
        long disputes = freelancer ? contracts.countFreelancerDisputes(user.getId())
                : contracts.countClientDisputes(user.getId());
        ProfileResponse.Reputation reputation = new ProfileResponse.Reputation(completed, funded, disputes,
                0, null, null, null, null, Instant.now());
        ProfileResponse.Verification verification = new ProfileResponse.Verification(
                row == null ? "UNVERIFIED" : row.getEmailVerification(),
                row == null ? "UNVERIFIED" : row.getIdentityVerification(),
                row == null ? "UNVERIFIED" : row.getPaymentVerification(), "NOT_CONFIGURED");
        return new ProfileResponse(user.getId(), user.getUserType(), user.getDisplayName(), own ? user.getEmail() : null,
                row == null ? 0 : row.getVersion(), row == null ? null : row.getAvatarUrl(),
                row == null ? null : row.getHeadline(), row == null ? null : row.getBio(),
                row == null ? null : row.getCountryCode(), readLanguages(row), readSkills(row),
                freelancer && row != null ? row.getHourlyRateUsd() : null,
                freelancer && row != null ? row.getAvailability() : null,
                !freelancer && row != null ? row.getCompanyName() : null,
                !freelancer && row != null ? row.getCompanyWebsite() : null,
                verification, reputation);
    }

    private PortfolioResponse item(PortfolioItem row) {
        return new PortfolioResponse(row.getId(), row.getUserId(), row.getVersion(), row.getTitle(),
                row.getDescription(), row.getProjectUrl(), row.getThumbnailUrl(),
                readList(row.getSkillsJson(), new TypeReference<>() {}), row.getCompletedAt(), row.getSortOrder());
    }

    private User user(UUID id) {
        return users.findById(id).filter(User::isEnabled)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ACCOUNT_NOT_FOUND));
    }
    private User lockedUser(UUID id) {
        return users.findWithLockById(id).filter(User::isEnabled)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ACCOUNT_NOT_FOUND));
    }
    private void freelancer(UUID id) {
        if (lockedUser(id).getUserType() != UserType.FREELANCER)
            throw new ApplicationException(ErrorCode.NOT_A_FREELANCER);
    }
    private MemberProfile blank(UUID userId) {
        MemberProfile row = new MemberProfile(); row.setUserId(userId); return row;
    }
    private void checkFields(ObjectNode input, Set<String> allowed) {
        if (input == null || !input.fieldNames().hasNext()) throw invalid();
        input.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw invalid(); });
    }
    private long version(ObjectNode input) {
        JsonNode v = input.get("version");
        if (v == null || !v.isIntegralNumber() || v.asLong() < 0) throw invalid();
        return v.asLong();
    }
    private String requiredText(JsonNode value, int min, int max) {
        if (value == null || !value.isTextual()) throw invalid();
        String text = value.asText().trim();
        if (text.length() < min || text.length() > max) throw invalid();
        return text;
    }
    private String optionalText(JsonNode value, int max) {
        if (value == null || value.isNull()) return null;
        return requiredText(value, 1, max);
    }
    private String url(JsonNode value) {
        String text = optionalText(value, 2048);
        if (text == null) return null;
        try {
            URI uri = URI.create(text);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) throw invalid();
            return text;
        } catch (IllegalArgumentException ex) { throw invalid(); }
    }
    private String country(JsonNode value) {
        String text = optionalText(value, 2);
        if (text == null) return null;
        text = text.toUpperCase(Locale.ROOT);
        if (!Arrays.asList(Locale.getISOCountries()).contains(text)) throw invalid();
        return text;
    }
    private BigDecimal rate(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isNumber()) throw invalid();
        BigDecimal amount = value.decimalValue();
        if (amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("999999.99")) > 0) throw invalid();
        return amount;
    }
    private List<String> skills(JsonNode value) {
        if (value == null || !value.isArray() || value.size() > 20) throw invalid();
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode node : value) {
            String skill = requiredText(node, 2, 40);
            if (!seen.add(skill.toLowerCase(Locale.ROOT))) throw invalid();
            result.add(skill);
        }
        return result;
    }
    private List<ProfileResponse.Language> languages(JsonNode value) {
        if (value == null || !value.isArray() || value.size() > 20) throw invalid();
        List<ProfileResponse.Language> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode node : value) {
            if (!node.isObject() || node.size() != 2) throw invalid();
            String code = requiredText(node.get("code"), 2, 35);
            String proficiency = requiredText(node.get("proficiency"), 2, 40);
            if (!code.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")
                    || !seen.add(code.toLowerCase(Locale.ROOT))) throw invalid();
            result.add(new ProfileResponse.Language(code, proficiency));
        }
        return result;
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private List<String> readSkills(MemberProfile row) {
        return row == null ? List.of() : readList(row.getSkillsJson(), new TypeReference<>() {});
    }
    private List<ProfileResponse.Language> readLanguages(MemberProfile row) {
        return row == null ? List.of() : readList(row.getLanguagesJson(), new TypeReference<>() {});
    }
    private <T> List<T> readList(String value, TypeReference<List<T>> type) {
        try { return mapper.readValue(value, type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid stored profile JSON", ex); }
    }
    private ApplicationException invalid() { return new ApplicationException(ErrorCode.PROFILE_INVALID); }
}
