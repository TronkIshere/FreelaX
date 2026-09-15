package com.misa.backend.entity;

import com.misa.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "payout_transaction")
public class PayoutTransaction extends AbstractEntity<UUID> {

    @Column(name = "platform_payout_id", nullable = false, unique = true)
    String platformPayoutId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxpayer_id", nullable = false)
    Taxpayer taxpayer;

    @Column(name = "blockchain", nullable = false)
    String blockchain = "solana";

    @Column(name = "transaction_hash", nullable = false)
    String transactionHash;

    @Column(name = "description")
    String description;

    @Column(name = "amount_usdc", nullable = false, precision = 20, scale = 6)
    BigDecimal amountUsdc;

    @Column(name = "exchange_rate", nullable = false, precision = 20, scale = 6)
    BigDecimal exchangeRate;

    @Column(name = "amount_vnd_gross", nullable = false, precision = 20, scale = 2)
    BigDecimal amountVndGross;

    @Column(name = "payment_date", nullable = false)
    LocalDate paymentDate;
}
