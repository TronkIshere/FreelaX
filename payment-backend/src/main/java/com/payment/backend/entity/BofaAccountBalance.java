package com.payment.backend.entity;

import com.payment.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "bofa_account_balance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"bank_account_number"}))
public class BofaAccountBalance extends AbstractEntity<UUID> {

    @Column(name = "bank_account_number", nullable = false, length = 34)
    String bankAccountNumber;

    @Column(name = "balance", nullable = false, precision = 20, scale = 6)
    BigDecimal balance;
}