package com.marketplace.backend.dto.response.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.marketplace.backend.entity.UserType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(UUID userId, UserType userType, String displayName, String email,
                              long version, String avatarUrl, String headline, String bio,
                              String countryCode, List<Language> languages, List<String> skills,
                              BigDecimal hourlyRateUsd, String availability, String companyName,
                              String companyWebsite, Verification verification,
                              Reputation reputation) {
    public record Language(String code, String proficiency) {}
    public record Verification(String email, String identity, String paymentMethod, String source) {}
    public record Reputation(long completedContracts, Long fundedContracts, long disputeCount,
                             long reviewCount, BigDecimal averageRating, BigDecimal onTimeRate,
                             BigDecimal paymentReleaseRate, BigDecimal medianReviewHours,
                             Instant calculatedAt) {}
}
