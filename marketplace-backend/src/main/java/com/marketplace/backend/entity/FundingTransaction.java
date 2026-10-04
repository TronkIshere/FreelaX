package com.marketplace.backend.entity;

import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "funding_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_funding_client_key", columnNames = {"client_user_id", "idempotency_key"}),
        indexes = @Index(name = "idx_funding_status_updated", columnList = "status,updated_at"))
public class FundingTransaction extends AbstractEntity<UUID> {
    @Version
    private long version;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(name = "milestone_id", nullable = false)
    private UUID milestoneId;
    @Column(name = "client_user_id", nullable = false)
    private UUID clientUserId;
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
    @Column(nullable = false, length = 64)
    private String payloadHash;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, length = 30)
    private String paymentMethodId;
    @Column(nullable = false, length = 20)
    private String payerBankCode;
    @Column(nullable = false, length = 34)
    private String payerBankAccountNumber;
    @Column(nullable = false)
    private String payerBankAccountHolderName;
    private UUID checkoutOrderId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FundingStatus status;
}
