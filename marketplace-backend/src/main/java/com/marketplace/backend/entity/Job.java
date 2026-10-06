package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "jobs")
@Getter
@Setter
public class Job extends AbstractEntity<UUID> {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'OTHER'")
    private JobCategory category = JobCategory.OTHER;

    @ElementCollection
    @CollectionTable(name = "job_skills", joinColumns = @JoinColumn(name = "job_id"))
    @OrderColumn(name = "skill_order")
    @Column(name = "skill", nullable = false, length = 40)
    private List<String> skills = new ArrayList<>();

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal budgetUsd;

    @Column(nullable = false)
    private UUID clientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    private UUID checkoutOrderId;

    @Column(length = 20)
    private String payerBankCode;

    @Column(length = 34)
    private String payerBankAccountNumber;

    private String payerBankAccountHolderName;

    @Column(name = "freelancer_id")
    private UUID freelancerId;

    private Instant deliveryDueAt;

    @Column(nullable = false)
    private int reviewWindowHours = 72;

    @Column(nullable = false)
    private int maxRevisions = 2;

    private UUID misaPayoutTransactionId;
    private UUID misaCertificateId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TaxExportStatus taxExportStatus = TaxExportStatus.NOT_ATTEMPTED;
}
