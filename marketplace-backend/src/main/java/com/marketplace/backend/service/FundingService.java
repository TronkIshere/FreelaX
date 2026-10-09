package com.marketplace.backend.service;

import com.marketplace.backend.client.PaymentBackendClient;
import com.marketplace.backend.dto.request.funding.FundMilestoneRequest;
import com.marketplace.backend.dto.response.bofa.CheckoutOrderResult;
import com.marketplace.backend.dto.response.funding.FundingResponse;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundingService {
    private static final String BANK_ACCOUNT_ON_FILE = "BANK_ACCOUNT_ON_FILE";
    private final FundingTransactionRepository transactions;
    private final WorkContractRepository contracts;
    private final EscrowContractRepository escrowContracts;
    private final MilestoneRepository milestones;
    private final JobRepository jobs;
    private final UserRepository users;
    private final PaymentBackendClient payment;
    private final NotificationService notifications;
    private final TransactionTemplate transactionTemplate;

    public FundingResponse fund(UUID clientId, UUID contractId, UUID milestoneId,
                                String idempotencyKey, FundMilestoneRequest request) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 100
                || request == null || request.getExpectedAmount() == null
                || request.getExpectedAmount().getAmount() == null
                || !StringUtils.hasText(request.getExpectedAmount().getCurrency())
                || request.getExpectedAmount().getAmount().signum() <= 0
                || request.getExpectedAmount().getAmount().scale() > 2
                || !BANK_ACCOUNT_ON_FILE.equals(request.getPaymentMethodId())) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
        String key = idempotencyKey.trim();
        if (key.isEmpty()) throw new ApplicationException(ErrorCode.INVALID_DATA);
        String currency = request.getExpectedAmount().getCurrency().trim().toUpperCase();
        BigDecimal amount = request.getExpectedAmount().getAmount().setScale(2);
        String hash = hash(contractId + ":" + milestoneId + ":" + amount.toPlainString()
                + ":" + currency + ":" + request.getPaymentMethodId());

        boolean[] createdNow = {false};
        FundingTransaction record = transactionTemplate.execute(status -> {
            WorkContract contract = contracts.findById(contractId)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
            if (!contract.getClientUserId().equals(clientId)) {
                throw new ApplicationException(ErrorCode.FUNDING_NOT_FOUND);
            }
            Milestone milestone = milestones.findWithLockById(milestoneId)
                    .filter(m -> m.getContractId().equals(contractId))
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
            if (escrowContracts.existsByContractId(contractId)
                    || "PARTNER_ESCROW_MOCK".equals(contract.getPaymentRail())
                    || PaymentFlow.RAIL.equals(contract.getPaymentRail())) {
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            }
            FundingTransaction prior = transactions.findByClientUserIdAndIdempotencyKey(clientId, key).orElse(null);
            if (prior != null) {
                if (!prior.getPayloadHash().equals(hash)) {
                    throw new ApplicationException(ErrorCode.FUNDING_KEY_CONFLICT);
                }
                return prior;
            }
            if (milestone.getStatus() != MilestoneStatus.PENDING_FUNDING
                    || contract.getStatus() != ContractStatus.PENDING_FUNDING) {
                throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
            }
            if (!"USD".equals(currency) || !currency.equals(milestone.getCurrency())
                    || amount.compareTo(milestone.getAmount()) != 0
                    || amount.compareTo(contract.getBudgetUsd()) != 0) {
                throw new ApplicationException(ErrorCode.FUNDING_AMOUNT_CHANGED);
            }
            if (transactions.existsByMilestoneIdAndStatusIn(milestoneId,
                    EnumSet.of(FundingStatus.PENDING, FundingStatus.PROCESSING,
                            FundingStatus.UNKNOWN, FundingStatus.SUCCEEDED))) {
                throw new ApplicationException(ErrorCode.FUNDING_IN_PROGRESS);
            }
            User client = users.findById(clientId)
                    .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID));
            if (client.getUserType() != UserType.CLIENT || client.getBankCode() == null
                    || !StringUtils.hasText(client.getBankAccountNumber())
                    || !StringUtils.hasText(client.getBankAccountHolderName())) {
                throw new ApplicationException(ErrorCode.FUNDING_PAYMENT_METHOD_INVALID);
            }
            FundingTransaction created = new FundingTransaction();
            created.setContractId(contractId);
            created.setMilestoneId(milestoneId);
            created.setClientUserId(clientId);
            created.setIdempotencyKey(key);
            created.setPayloadHash(hash);
            created.setAmount(amount);
            created.setCurrency(currency);
            created.setPaymentMethodId(BANK_ACCOUNT_ON_FILE);
            created.setPayerBankCode(client.getBankCode().name());
            created.setPayerBankAccountNumber(client.getBankAccountNumber());
            created.setPayerBankAccountHolderName(client.getBankAccountHolderName());
            created.setStatus(FundingStatus.PENDING);
            createdNow[0] = true;
            return transactions.saveAndFlush(created);
        });
        if (createdNow[0]) {
            process(record.getId());
        }
        return transactionTemplate.execute(status -> response(transactions.findById(record.getId()).orElseThrow()));
    }

    public FundingResponse get(UUID userId, UUID contractId, UUID milestoneId, UUID transactionId) {
        WorkContract contract = contracts.findById(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        if (!contract.getClientUserId().equals(userId) && !contract.getFreelancerId().equals(userId)) {
            throw new ApplicationException(ErrorCode.FUNDING_NOT_FOUND);
        }
        FundingTransaction record = transactions.findById(transactionId)
                .filter(t -> t.getContractId().equals(contractId) && t.getMilestoneId().equals(milestoneId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        return response(record);
    }

    public FundingResponse latest(UUID userId, UUID contractId, UUID milestoneId) {
        WorkContract contract = contracts.findById(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        if (!contract.getClientUserId().equals(userId) && !contract.getFreelancerId().equals(userId)) {
            throw new ApplicationException(ErrorCode.FUNDING_NOT_FOUND);
        }
        milestones.findById(milestoneId)
                .filter(m -> m.getContractId().equals(contractId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.FUNDING_NOT_FOUND));
        return transactions.findFirstByMilestoneIdOrderByCreatedAtDesc(milestoneId)
                .map(this::response).orElse(null);
    }

    @Scheduled(initialDelayString = "${funding.reconcile-initial-delay-ms:30000}",
            fixedDelayString = "${funding.reconcile-interval-ms:30000}")
    public void reconcile() {
        for (FundingTransaction record : transactions.findTop50ByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                EnumSet.of(FundingStatus.PENDING, FundingStatus.PROCESSING, FundingStatus.UNKNOWN),
                LocalDateTime.now().minusSeconds(30))) {
            if ("PARTNER_ESCROW_MOCK".equals(record.getPaymentMethodId())) continue;
            try {
                process(record.getId());
            } catch (RuntimeException ex) {
                log.warn("Funding reconciliation will retry transaction {}", record.getId());
            }
        }
    }

    private void process(UUID transactionId) {
        FundingTransaction existing = transactions.findById(transactionId).orElseThrow();
        if (contracts.findById(existing.getContractId())
                .map(c -> PaymentFlow.RAIL.equals(c.getPaymentRail())).orElse(false)) {
            mark(transactionId, FundingStatus.UNKNOWN);
            return;
        }
        FundingTransaction record = transactionTemplate.execute(status -> {
            FundingTransaction locked = transactions.findWithLockById(transactionId).orElseThrow();
            if (locked.getStatus() == FundingStatus.SUCCEEDED || locked.getStatus() == FundingStatus.FAILED) return null;
            locked.setStatus(FundingStatus.PROCESSING);
            return transactions.save(locked);
        });
        if (record == null) return;

        String providerKey = "marketplace-funding-" + transactionId;
        CheckoutOrderResult order;
        try {
            order = record.getCheckoutOrderId() != null
                    ? payment.getFundingOrder(record.getCheckoutOrderId())
                    : payment.findFundingOrder(providerKey);
            if (order == null) {
                order = payment.createFundingOrder(record.getClientUserId(),
                        contracts.findById(record.getContractId()).orElseThrow().getJobId(),
                        record.getAmount(), record.getPayerBankCode(),
                        record.getPayerBankAccountNumber(), record.getPayerBankAccountHolderName(), providerKey);
            }
            validateProviderOrder(record, order);
            UUID orderId = order.getId();
            transactionTemplate.executeWithoutResult(status -> {
                FundingTransaction locked = transactions.findWithLockById(transactionId).orElseThrow();
                locked.setCheckoutOrderId(orderId);
            });
            if (!"CAPTURED".equals(order.getStatus())) {
                order = payment.captureFundingOrder(orderId);
            }
            if (order == null || !"CAPTURED".equals(order.getStatus())) {
                mark(transactionId, FundingStatus.UNKNOWN);
                return;
            }
            validateProviderOrder(record, order);
            transactionTemplate.executeWithoutResult(status -> {
                FundingTransaction locked = transactions.findWithLockById(transactionId).orElseThrow();
                Milestone milestone = milestones.findWithLockById(locked.getMilestoneId()).orElseThrow();
                WorkContract contract = contracts.findById(locked.getContractId()).orElseThrow();
                Job job = jobs.findById(contract.getJobId()).orElseThrow();
                if (locked.getStatus() == FundingStatus.SUCCEEDED) return;
                if (milestone.getStatus() != MilestoneStatus.PENDING_FUNDING) {
                    throw new ApplicationException(ErrorCode.FUNDING_INVALID_STATE);
                }
                milestone.setStatus(MilestoneStatus.FUNDED);
                contract.setStatus(ContractStatus.ACTIVE);
                job.setCheckoutOrderId(orderId);
                job.setPayerBankCode(locked.getPayerBankCode());
                job.setPayerBankAccountNumber(locked.getPayerBankAccountNumber());
                job.setPayerBankAccountHolderName(locked.getPayerBankAccountHolderName());
                job.setStatus(JobStatus.IN_PROGRESS);
                locked.setStatus(FundingStatus.SUCCEEDED);
                notifications.notify(contract.getFreelancerId(), NotificationType.FUNDING_CONFIRMED,
                        "Milestone đã được funding", "Bạn có thể bắt đầu công việc \"" + job.getTitle() + "\".", job.getId());
            });
        } catch (HttpClientErrorException ex) {
            // A definitive provider rejection is safe to retry with a new client key.
            mark(transactionId, FundingStatus.FAILED);
        } catch (RestClientException ex) {
            // The request may have reached the provider; the scheduler must inspect it.
            mark(transactionId, FundingStatus.UNKNOWN);
        } catch (ApplicationException ex) {
            // A mismatched provider record needs investigation, never another charge.
            mark(transactionId, FundingStatus.UNKNOWN);
        }
    }

    private void validateProviderOrder(FundingTransaction record, CheckoutOrderResult order) {
        UUID jobId = contracts.findById(record.getContractId()).orElseThrow().getJobId();
        if (order == null || order.getId() == null
                || !record.getClientUserId().equals(order.getPayerUserId())
                || !jobId.equals(order.getJobId())
                || order.getAmountUsd() == null
                || record.getAmount().compareTo(order.getAmountUsd()) != 0
                || !record.getPayerBankAccountNumber().equals(order.getPayerBankAccountNumber())) {
            throw new ApplicationException(ErrorCode.PAYMENT_BACKEND_CALL_FAILED, "funding order mismatch");
        }
    }

    private void mark(UUID id, FundingStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            FundingTransaction record = transactions.findWithLockById(id).orElseThrow();
            if (record.getStatus() != FundingStatus.SUCCEEDED) record.setStatus(status);
        });
    }

    private FundingResponse response(FundingTransaction record) {
        FundingStatus status = record.getStatus();
        return FundingResponse.builder()
                .fundingTransactionId(record.getId())
                .fundingStatus(status.name())
                .simulation(true)
                .providerReference(record.getCheckoutOrderId())
                .nextAction(status == FundingStatus.SUCCEEDED ? "START_WORK"
                        : status == FundingStatus.FAILED ? "RETRY_WITH_NEW_KEY" : "WAIT_FOR_RECONCILIATION")
                .retryAfterSeconds(status == FundingStatus.PENDING || status == FundingStatus.PROCESSING
                        || status == FundingStatus.UNKNOWN ? 30 : null)
                .build();
    }

    private static String hash(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
