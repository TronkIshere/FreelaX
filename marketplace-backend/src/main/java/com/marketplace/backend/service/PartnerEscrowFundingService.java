package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.response.funding.FundingResponse;
import com.marketplace.backend.dto.response.partner.PartnerEscrowResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerEscrowFundingService {
    public static final String RAIL = "PARTNER_ESCROW_MOCK";
    private final FundingTransactionRepository transactions;
    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final JobRepository jobs;
    private final UserRepository users;
    private final EscrowContractRepository solanaEscrows;
    private final PaymentBackendClient payment;
    private final PartnerReconciliationService partnerReconciliation;
    private final NotificationService notifications;
    private final TransactionTemplate transactionTemplate;

    public FundingResponse fund(UUID clientId, UUID contractId, UUID milestoneId, String key) {
        if (!StringUtils.hasText(key) || key.length() > 100 || !key.equals(key.trim()))
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        String fingerprint = hash(clientId + ":" + contractId + ":" + milestoneId);
        FundingTransaction record = transactionTemplate.execute(tx -> {
            Milestone milestone = milestones.findWithLockById(milestoneId)
                    .filter(m -> m.getContractId().equals(contractId))
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
            WorkContract contract = contracts.findById(contractId)
                    .filter(c -> clientId.equals(c.getClientUserId()))
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
            Job job = jobs.findById(contract.getJobId()).orElseThrow();
            if (solanaEscrows.existsByContractId(contractId)
                    || contract.getPaymentRail() != null && !RAIL.equals(contract.getPaymentRail()))
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            FundingTransaction prior = transactions.findByClientUserIdAndIdempotencyKey(clientId, key).orElse(null);
            if (prior != null) {
                if (!RAIL.equals(prior.getPaymentMethodId()) || !fingerprint.equals(prior.getPayloadHash())
                        || !prior.getMilestoneId().equals(milestoneId))
                    throw new ApplicationException(ErrorCode.FUNDING_KEY_CONFLICT);
                return prior;
            }
            if (contract.getStatus() != ContractStatus.PENDING_FUNDING
                    || milestone.getStatus() != MilestoneStatus.PENDING_FUNDING
                    || job.getStatus() != JobStatus.AWAITING_PAYMENT
                    || milestone.getAmount().compareTo(contract.getBudgetUsd()) != 0
                    || !"USD".equals(milestone.getCurrency())
                    || transactions.existsByMilestoneIdAndStatusIn(milestoneId,
                            EnumSet.of(FundingStatus.PENDING, FundingStatus.PROCESSING,
                                    FundingStatus.UNKNOWN, FundingStatus.SUCCEEDED)))
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            User client = users.findById(clientId)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID));
            if (client.getBankCode() == null || !StringUtils.hasText(client.getBankAccountNumber())
                    || !StringUtils.hasText(client.getBankAccountHolderName()))
                throw new ApplicationException(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID);
            FundingTransaction created = new FundingTransaction();
            created.setContractId(contractId);
            created.setMilestoneId(milestoneId);
            created.setClientUserId(clientId);
            created.setIdempotencyKey(key);
            created.setPayloadHash(fingerprint);
            created.setAmount(milestone.getAmount());
            created.setCurrency("USD");
            created.setPaymentMethodId(RAIL);
            created.setPayerBankCode(client.getBankCode().name());
            created.setPayerBankAccountNumber(client.getBankAccountNumber());
            created.setPayerBankAccountHolderName(client.getBankAccountHolderName());
            created.setStatus(FundingStatus.PENDING);
            contract.setPaymentRail(RAIL);
            return transactions.saveAndFlush(created);
        });
        process(record.getId());
        return response(transactions.findById(record.getId()).orElseThrow());
    }

    public FundingResponse get(UUID actor, UUID contractId, UUID milestoneId) {
        WorkContract contract = contracts.findById(contractId)
                .filter(c -> actor.equals(c.getClientUserId()) || actor.equals(c.getFreelancerId()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        if (!RAIL.equals(contract.getPaymentRail())) throw new ApplicationException(ErrorCode.FUNDING_NOT_FOUND);
        FundingTransaction record = transactions.findFirstByMilestoneIdOrderByCreatedAtDesc(milestoneId)
                .filter(x -> x.getContractId().equals(contractId) && RAIL.equals(x.getPaymentMethodId()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        return response(record);
    }

    @Scheduled(initialDelayString = "${partner-mock.reconcile-initial-delay-ms:5000}",
            fixedDelayString = "${partner-mock.reconcile-interval-ms:5000}")
    public void reconcile() {
        for (FundingTransaction row : transactions.findTop50ByPaymentMethodIdAndStatusInOrderByUpdatedAtAsc(
                RAIL, EnumSet.of(FundingStatus.PENDING, FundingStatus.PROCESSING, FundingStatus.UNKNOWN))) {
            try { process(row.getId()); }
            catch (RuntimeException ex) { log.warn("Partner mock funding reconciliation retry: {}", row.getId()); }
        }
    }

    private void process(UUID transactionId) {
        FundingTransaction record = transactions.findById(transactionId).orElseThrow();
        if (record.getStatus() == FundingStatus.SUCCEEDED) return;
        WorkContract contract = contracts.findById(record.getContractId()).orElseThrow();
        if (!RAIL.equals(contract.getPaymentRail())) {
            markUnknown(transactionId);
            return;
        }
        PartnerEscrowResult partner;
        try {
            partner = payment.openPartnerEscrow(record.getMilestoneId(), contract.getId(), contract.getJobId(),
                    contract.getClientUserId(), contract.getFreelancerId(), record.getAmount(),
                    "partner-fund-" + record.getMilestoneId());
        } catch (RestClientException ex) {
            markUnknown(transactionId);
            return;
        }
        if (!matches(record, contract, partner)) {
            markUnknown(transactionId);
            return;
        }
        if (!"FUNDED".equals(partner.status())) return;
        try {
            if (!partnerReconciliation.confirms(record.getMilestoneId(), "FUND", record.getAmount())) {
                markUnknown(transactionId);
                return;
            }
        } catch (RestClientException ex) {
            markUnknown(transactionId);
            return;
        }
        transactionTemplate.executeWithoutResult(tx -> {
            FundingTransaction locked = transactions.findWithLockById(transactionId).orElseThrow();
            Milestone milestone = milestones.findWithLockById(locked.getMilestoneId()).orElseThrow();
            WorkContract c = contracts.findById(locked.getContractId()).orElseThrow();
            Job job = jobs.findById(c.getJobId()).orElseThrow();
            if (locked.getStatus() == FundingStatus.SUCCEEDED) return;
            if (milestone.getStatus() != MilestoneStatus.PENDING_FUNDING
                    || c.getStatus() != ContractStatus.PENDING_FUNDING || !RAIL.equals(c.getPaymentRail()))
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            locked.setCheckoutOrderId(locked.getMilestoneId());
            locked.setStatus(FundingStatus.SUCCEEDED);
            milestone.setStatus(MilestoneStatus.FUNDED);
            c.setStatus(ContractStatus.ACTIVE);
            job.setStatus(JobStatus.IN_PROGRESS);
            job.setCheckoutOrderId(locked.getMilestoneId());
            notifications.notify(c.getFreelancerId(), NotificationType.FUNDING_CONFIRMED,
                    "Đã ký quỹ mô phỏng", "Đối tác mock đã xác nhận đủ USD; bạn có thể bắt đầu công việc.", job.getId());
        });
    }

    private boolean matches(FundingTransaction row, WorkContract c, PartnerEscrowResult p) {
        return p != null && p.simulation() && Objects.equals(p.milestoneId(), row.getMilestoneId())
                && Objects.equals(p.contractId(), c.getId()) && Objects.equals(p.jobId(), c.getJobId())
                && Objects.equals(p.clientId(), c.getClientUserId())
                && Objects.equals(p.freelancerId(), c.getFreelancerId())
                && p.grossUsd() != null && p.grossUsd().compareTo(row.getAmount()) == 0;
    }

    private void markUnknown(UUID transactionId) {
        transactionTemplate.executeWithoutResult(tx -> {
            FundingTransaction locked = transactions.findWithLockById(transactionId).orElseThrow();
            if (locked.getStatus() != FundingStatus.SUCCEEDED) locked.setStatus(FundingStatus.UNKNOWN);
        });
    }

    private FundingResponse response(FundingTransaction row) {
        return FundingResponse.builder().fundingTransactionId(row.getId())
                .fundingStatus(row.getStatus().name()).simulation(true)
                .providerReference(row.getCheckoutOrderId())
                .nextAction(row.getStatus() == FundingStatus.SUCCEEDED ? "START_WORK" : "WAIT_FOR_RECONCILIATION")
                .retryAfterSeconds(row.getStatus() == FundingStatus.SUCCEEDED ? null : 5).build();
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
