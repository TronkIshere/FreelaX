package com.marketplace.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A two-party authorization in progress (register F13, V7): both parties sign the same typed data (deadline, digest). */
@Getter
@Setter
@Entity
@Table(name = "avalanche_pending_authorizations")
public class AvalanchePendingAuthorization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(nullable = false, length = 30)
    private String action;
    @Column(name = "authorization_deadline", nullable = false)
    private long authorizationDeadline;
    @Column(name = "typed_data_digest", nullable = false, length = 66)
    private String typedDataDigest;
    @Column(name = "client_signature", length = 132)
    private String clientSignature;
    @Column(name = "freelancer_signature", length = 132)
    private String freelancerSignature;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
