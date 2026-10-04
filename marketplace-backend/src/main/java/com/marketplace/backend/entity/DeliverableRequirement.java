package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "deliverable_requirements")
public class DeliverableRequirement extends AbstractEntity<UUID> {
    @Column(name = "job_id")
    private UUID jobId;
    @Column(name = "contract_id")
    private UUID contractId;
    @Column(name = "item_order", nullable = false)
    private int order;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(nullable = false)
    private boolean required;
}
