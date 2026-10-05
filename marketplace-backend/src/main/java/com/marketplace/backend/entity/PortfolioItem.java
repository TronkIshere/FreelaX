package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "portfolio_items", indexes = @Index(name = "idx_portfolio_owner_sort", columnList = "user_id,sort_order"))
@Getter @Setter
public class PortfolioItem extends AbstractEntity<UUID> {
    @Version
    private long version;
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(length = 2048)
    private String projectUrl;
    @Column(length = 2048)
    private String thumbnailUrl;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String skillsJson = "[]";
    private LocalDate completedAt;
    @Column(nullable = false)
    private int sortOrder;
}
