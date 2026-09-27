package com.marketplace.backend.entity;

// Da xac nhan qua Notification.java (uploaded): AbstractEntity<UUID> cung
// cap san id/createdAt/updatedAt.
import com.marketplace.backend.entity.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "freelancer_payout_records")
public class FreelancerPayoutRecord extends AbstractEntity<UUID> {

    @Column(nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private UUID freelancerId;

    @Column(nullable = false)
    private UUID clientUserId;

    // ----- USD -> USDC (on-ramp) -----

    /** So tien goc cua job (ngan sach USD), truoc khi quy doi. */
    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amountUsd;

    /** Quy doi peg 1:1, CHUA tru phi on-ramp -- dung lam co so khai thue. */
    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amountUsdcGross;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal onRampFeeUsdc;

    /** amountUsdcGross - onRampFeeUsdc -- so thuc di tiep sang off-ramp. */
    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amountUsdcNet;

    // ----- USDC -> VND (off-ramp) -----

    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal exchangeRateUsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RateSource rateSource;

    /** = amountUsdcGross * exchangeRateUsed -- so gui bao cao thue (Misa). */
    @Column(nullable = false, precision = 20, scale = 0)
    private BigDecimal amountVndGross;

    @Column(nullable = false, precision = 20, scale = 0)
    private BigDecimal offRampFeeVnd;

    /** So tien freelancer THUC SU nhan duoc (sau tat ca cac buoc/phi). */
    @Column(nullable = false, precision = 20, scale = 0)
    private BigDecimal amountVndActual;

    // ----- Lien ket tham chieu sang misa-backend -----

    private UUID misaPayoutTransactionId;

    private UUID misaCertificateId;

    public enum RateSource {
        LIVE_COINGECKO,
        FALLBACK_PLACEHOLDER
    }
}