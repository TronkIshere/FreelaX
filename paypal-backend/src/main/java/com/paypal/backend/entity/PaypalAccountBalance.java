package com.paypal.backend.entity;

import com.paypal.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "paypal_account_balance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "account_role", "currency"}))
public class PaypalAccountBalance extends AbstractEntity<UUID> {

    @Column(name = "account_id", nullable = false)
    UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_role", nullable = false, length = 10)
    PaypalAccountRole accountRole;

    @Column(name = "currency", nullable = false, length = 10)
    String currency;

    @Column(name = "balance", nullable = false, precision = 20, scale = 6)
    BigDecimal balance = BigDecimal.ZERO;
}