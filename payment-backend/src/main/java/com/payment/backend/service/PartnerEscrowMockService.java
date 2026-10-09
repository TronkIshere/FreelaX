package com.payment.backend.service;

import com.payment.backend.entity.PartnerEscrowMock;
import com.payment.backend.entity.PartnerStatementMock;
import com.payment.backend.exception.ApplicationException;
import com.payment.backend.exception.ErrorCode;
import com.payment.backend.repository.PartnerEscrowMockRepository;
import com.payment.backend.repository.PartnerStatementMockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Independent mock partner statement. No bank account or real USD is controlled here. */
@Service
@RequiredArgsConstructor
public class PartnerEscrowMockService {
    private static final BigDecimal FEE_RATE = new BigDecimal("0.03");
    private final PartnerEscrowMockRepository escrows;
    private final PartnerStatementMockRepository statements;

    @Value("${partner-mock.usd-vnd-rate:25000}")
    private BigDecimal mockRate;

    public record OpenRequest(UUID milestoneId, UUID contractId, UUID jobId, UUID clientId,
                              UUID freelancerId, BigDecimal grossUsd, String fundKey) {}
    public record ReleaseRequest(String releaseKey, String bankCode, String bankAccountNumber) {}
    public record RefundRequest(String refundKey) {}
    public record EscrowView(UUID milestoneId, UUID contractId, UUID jobId, UUID clientId,
                             UUID freelancerId, BigDecimal grossUsd, String status,
                             BigDecimal feeUsd, BigDecimal freelancerUsd, BigDecimal usdVndRate,
                             BigDecimal payoutVnd, String releaseKey, String refundKey,
                             String recipientBankCode, String recipientBankLast4,
                             Instant rateLockedAt, Instant updatedAt, boolean simulation) {}
    public record StatementRow(UUID milestoneId, String eventKey, String kind,
                               BigDecimal deltaUsd, Instant occurredAt) {}
    public record StatementView(BigDecimal balanceUsd, List<StatementRow> entries,
                                Instant observedAt, boolean simulation) {}

    @Transactional
    public EscrowView open(OpenRequest request) {
        if (request == null || request.milestoneId() == null || request.contractId() == null
                || request.jobId() == null || request.clientId() == null || request.freelancerId() == null
                || request.clientId().equals(request.freelancerId()) || request.grossUsd() == null
                || request.grossUsd().signum() <= 0 || request.grossUsd().scale() > 2
                || !key(request.fundKey())) throw invalid();
        PartnerEscrowMock prior = escrows.findWithLockByMilestoneId(request.milestoneId()).orElse(null);
        if (prior != null) {
            if (!prior.getContractId().equals(request.contractId()) || !prior.getJobId().equals(request.jobId())
                    || !prior.getClientId().equals(request.clientId())
                    || !prior.getFreelancerId().equals(request.freelancerId())
                    || prior.getGrossUsd().compareTo(request.grossUsd()) != 0
                    || !prior.getFundKey().equals(request.fundKey())) throw conflict();
            return view(prior);
        }
        Instant now = Instant.now();
        PartnerEscrowMock row = new PartnerEscrowMock();
        row.setMilestoneId(request.milestoneId());
        row.setContractId(request.contractId());
        row.setJobId(request.jobId());
        row.setClientId(request.clientId());
        row.setFreelancerId(request.freelancerId());
        row.setGrossUsd(request.grossUsd().setScale(2));
        row.setFundKey(request.fundKey());
        row.setStatus("PENDING");
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return view(escrows.saveAndFlush(row));
    }

    /** Called by the mock partner scheduler, never inferred from a Marketplace request. */
    @Transactional
    public EscrowView confirmFunding(UUID milestoneId) {
        PartnerEscrowMock row = locked(milestoneId);
        if ("PENDING".equals(row.getStatus())) {
            row.setStatus("FUNDED");
            row.setUpdatedAt(Instant.now());
            statement(row.getMilestoneId(), "fund:" + row.getMilestoneId(), "FUND", row.getGrossUsd());
        }
        return view(row);
    }

    @Transactional(readOnly = true)
    public EscrowView get(UUID milestoneId) {
        return view(escrows.findById(milestoneId).orElseThrow(this::missing));
    }

    @Transactional
    public EscrowView freeze(UUID milestoneId) {
        PartnerEscrowMock row = locked(milestoneId);
        if ("FUNDED".equals(row.getStatus())) {
            row.setStatus("FROZEN");
            row.setUpdatedAt(Instant.now());
        } else if (!"FROZEN".equals(row.getStatus())) throw conflict();
        return view(row);
    }

    @Transactional
    public EscrowView release(UUID milestoneId, ReleaseRequest request, boolean adminResolution) {
        if (request == null || !key(request.releaseKey()) || !StringUtils.hasText(request.bankCode())
                || !StringUtils.hasText(request.bankAccountNumber()) || request.bankAccountNumber().length() < 4
                || request.bankAccountNumber().length() > 34 || request.bankCode().length() > 30
                || !request.bankCode().equals(request.bankCode().trim())
                || !request.bankAccountNumber().equals(request.bankAccountNumber().trim())) throw invalid();
        PartnerEscrowMock row = locked(milestoneId);
        String bankHash = hash(request.bankCode() + ":" + request.bankAccountNumber());
        if ("PAID".equals(row.getStatus()) && request.releaseKey().equals(row.getReleaseKey())
                && bankHash.equals(row.getRecipientBankHash())) return view(row);
        if (!("FUNDED".equals(row.getStatus()) || adminResolution && "FROZEN".equals(row.getStatus()))
                || row.getReleaseKey() != null || row.getRefundKey() != null) throw conflict();
        BigDecimal rate = mockRate;
        if (rate == null || rate.signum() <= 0 || rate.scale() > 6) throw invalid();
        BigDecimal fee = row.getGrossUsd().multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal freelancerUsd = row.getGrossUsd().subtract(fee);
        row.setReleaseKey(request.releaseKey());
        row.setFeeUsd(fee);
        row.setFreelancerUsd(freelancerUsd);
        row.setUsdVndRate(rate.setScale(6, RoundingMode.UNNECESSARY));
        row.setPayoutVnd(freelancerUsd.multiply(rate).setScale(0, RoundingMode.HALF_UP));
        row.setRecipientBankCode(request.bankCode().trim());
        String number = request.bankAccountNumber().trim();
        row.setRecipientBankLast4(number.substring(number.length() - 4));
        row.setRecipientBankHash(bankHash);
        row.setRateLockedAt(Instant.now());
        row.setStatus("PAID");
        row.setUpdatedAt(Instant.now());
        statement(milestoneId, "release:" + milestoneId, "RELEASE", row.getGrossUsd().negate());
        return view(row);
    }

    @Transactional
    public EscrowView refund(UUID milestoneId, RefundRequest request, boolean adminResolution) {
        if (request == null || !key(request.refundKey())) throw invalid();
        PartnerEscrowMock row = locked(milestoneId);
        if ("REFUNDED".equals(row.getStatus()) && request.refundKey().equals(row.getRefundKey())) return view(row);
        if (!("FUNDED".equals(row.getStatus()) || adminResolution && "FROZEN".equals(row.getStatus()))
                || row.getReleaseKey() != null || row.getRefundKey() != null) throw conflict();
        row.setRefundKey(request.refundKey());
        row.setFeeUsd(BigDecimal.ZERO.setScale(2));
        row.setStatus("REFUNDED");
        row.setUpdatedAt(Instant.now());
        statement(milestoneId, "refund:" + milestoneId, "REFUND", row.getGrossUsd().negate());
        return view(row);
    }

    @Transactional(readOnly = true)
    public StatementView statement() {
        BigDecimal total = statements.totalUsd();
        List<StatementRow> entries = statements.findAll().stream()
                .map(x -> new StatementRow(x.getMilestoneId(), x.getEventKey(), x.getKind(),
                        x.getDeltaUsd(), x.getOccurredAt()))
                .toList();
        return new StatementView(total == null ? BigDecimal.ZERO.setScale(2) : total,
                entries, Instant.now(), true);
    }

    private void statement(UUID milestoneId, String key, String kind, BigDecimal delta) {
        PartnerStatementMock event = new PartnerStatementMock();
        event.setMilestoneId(milestoneId);
        event.setEventKey(key);
        event.setKind(kind);
        event.setDeltaUsd(delta.setScale(2));
        event.setOccurredAt(Instant.now());
        statements.saveAndFlush(event);
    }

    private PartnerEscrowMock locked(UUID milestoneId) {
        return escrows.findWithLockByMilestoneId(milestoneId).orElseThrow(this::missing);
    }
    private EscrowView view(PartnerEscrowMock row) {
        return new EscrowView(row.getMilestoneId(), row.getContractId(), row.getJobId(),
                row.getClientId(), row.getFreelancerId(), row.getGrossUsd(), row.getStatus(),
                row.getFeeUsd(), row.getFreelancerUsd(), row.getUsdVndRate(), row.getPayoutVnd(),
                row.getReleaseKey(), row.getRefundKey(), row.getRecipientBankCode(),
                row.getRecipientBankLast4(), row.getRateLockedAt(), row.getUpdatedAt(), true);
    }
    private boolean key(String value) {
        return StringUtils.hasText(value) && value.length() <= 100 && value.equals(value.trim());
    }
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
    private ApplicationException invalid() { return new ApplicationException(ErrorCode.INVALID_DATA); }
    private ApplicationException missing() { return new ApplicationException(ErrorCode.DATA_NOT_FOUND, "partner escrow"); }
    private ApplicationException conflict() { return new ApplicationException(ErrorCode.INVALID_TRANSACTION_STATUS); }
}
