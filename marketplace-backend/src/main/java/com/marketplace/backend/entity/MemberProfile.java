package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "member_profiles", uniqueConstraints = @UniqueConstraint(name = "uk_member_profile_user", columnNames = "user_id"))
@Getter @Setter
public class MemberProfile extends AbstractEntity<UUID> {
    @Version
    private long version;
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
    @Column(length = 2048)
    private String avatarUrl;
    @Column(length = 120)
    private String headline;
    @Column(length = 2000)
    private String bio;
    @Column(length = 2)
    private String countryCode;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String languagesJson = "[]";
    @Column(nullable = false, columnDefinition = "TEXT")
    private String skillsJson = "[]";
    @Column(precision = 12, scale = 2)
    private BigDecimal hourlyRateUsd;
    @Column(length = 40)
    private String availability;
    @Column(length = 120)
    private String companyName;
    @Column(length = 2048)
    private String companyWebsite;
    @Column(nullable = false, length = 24)
    private String emailVerification = "UNVERIFIED";
    @Column(nullable = false, length = 24)
    private String identityVerification = "UNVERIFIED";
    @Column(nullable = false, length = 24)
    private String paymentVerification = "UNVERIFIED";
    private Instant editedAt;
}
