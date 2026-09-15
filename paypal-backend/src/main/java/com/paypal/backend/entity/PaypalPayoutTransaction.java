package com.paypal.backend.entity;

import com.paypal.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "paypal_payout_transaction")
public class PaypalPayoutTransaction extends AbstractEntity<UUID> {

    @Column(name = "platform_payout_id", nullable = false, unique = true)
    String platformPayoutId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_id", nullable = false)
    PaypalPayee payee;

    @Column(name = "sender_reference")
    String senderReference;

    @Column(name = "description")
    String description;

    @Column(name = "gross_amount_usd", nullable = false, precision = 20, scale = 2)
    BigDecimal grossAmountUsd;

    @Column(name = "mid_market_rate", nullable = false, precision = 20, scale = 6)
    BigDecimal midMarketRate;

    @Column(name = "commercial_fee_usd", nullable = false, precision = 20, scale = 2)
    BigDecimal commercialFeeUsd;

    @Column(name = "fx_spread_cost_usd", nullable = false, precision = 20, scale = 2)
    BigDecimal fxSpreadCostUsd;

    @Column(name = "net_usd_after_fees", nullable = false, precision = 20, scale = 2)
    BigDecimal netUsdAfterFees;

    @Column(name = "net_vnd", nullable = false, precision = 20, scale = 2)
    BigDecimal netVnd;

    @Column(name = "effective_fee_rate_percent", nullable = false, precision = 6, scale = 2)
    BigDecimal effectiveFeeRatePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    PaypalTransactionStatus status = PaypalTransactionStatus.RECEIVED;

    @Column(name = "payment_date", nullable = false)
    LocalDate paymentDate;

    @Column(name = "withdrawn_at")
    LocalDateTime withdrawnAt;
}
