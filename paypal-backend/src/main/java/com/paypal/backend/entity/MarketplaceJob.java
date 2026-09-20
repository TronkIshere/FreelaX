package com.paypal.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "marketplace_jobs")
@Getter
@Setter
public class MarketplaceJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    UUID clientUserId;

    String title;

    String description;

    BigDecimal budgetUsd;

    UUID checkoutOrderId;

    @Enumerated(EnumType.STRING)
    JobStatus status;

    LocalDateTime createdAt;
}
