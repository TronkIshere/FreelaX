package com.marketplace.backend.service;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.dto.request.submission.CreateContractSubmissionRequest;
import com.marketplace.backend.dto.request.submission.ReviewSubmissionRequest;
import com.marketplace.backend.dto.response.solana.SolanaBuildResult;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SolanaEscrowWorkflowService {
    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final JobSubmissionRepository submissions;
    private final EscrowContractRepository escrows;
    private final EscrowActionIntentRepository intents;
    private final ContractSubmissionService submissionService;
    private final ContractDisputeService disputeService;
    private final SolanaCprClient solana;
    private final ObjectMapper objectMapper;

    public record ActionInput(CreateContractSubmissionRequest submission,
            ReviewSubmissionRequest review, String newDueAt,
            String reasonCode, String description) { }
    public record BuildView(UUID intentId, String action, String buildSessionId,
            String transactionBase64, String escrowAddress, String actorWallet,
            String reviewDueAt, String payloadHash) { }
    public record SignedInput(String transactionBase64) { }
    public record ActionStatus(UUID intentId, String action, String signature,
            String chainStatus) { }
    public record MutualRefundView(UUID intentId, String buildSessionId,
            String originalTransaction, String partialTransaction,
            String clientWallet, String freelancerWallet) { }

    @Transactional
    public BuildView build(UUID actorId, UUID contractId, String action, ActionInput input) {
        WorkContract contract = participant(actorId, contractId);
        EscrowContract record = escrows.findByContractId(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE));
        Milestone milestone = milestones.findById(record.getMilestoneId()).orElseThrow();
        SolanaEscrowResult chain = solana.findEscrow(record.getMilestoneId().toString())
                .orElseThrow(() -> new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE));
        if (!chain.address().equals(record.getEscrowAddress())) {
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        }
        boolean client = actorId.equals(contract.getClientUserId());
        String actorWallet = client ? record.getClientWallet() : record.getFreelancerWallet();
        String gatewayAction = action;
        String hash = null;
        String newDue = null;
        switch (action) {
            case "submit" -> {
                if (client || input == null || input.submission() == null
                        || !"Funded".equals(chain.status()) && !"Revision".equals(chain.status())
                        || milestone.getStatus() != MilestoneStatus.FUNDED
                            && milestone.getStatus() != MilestoneStatus.IN_PROGRESS) {
                    throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
                }
                submissionService.validateEscrowDraft(contractId, input.submission());
                hash = submissionService.escrowPayloadHash(contractId, input.submission());
            }
            case "release", "request-revision" -> {
                if (!client || !"Submitted".equals(chain.status())
                        || contract.getStatus() != ContractStatus.UNDER_REVIEW) {
                    throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
                }
                if ("request-revision".equals(action)) {
                    if (input == null || input.review() == null
                            || input.review().getDecision() != ReviewSubmissionRequest.Decision.REQUEST_REVISION)
                        throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
                    submissionService.validateEscrowReviewDraft(contractId, input.review());
                    JobSubmission latest = submissions.findFirstByContractIdOrderByVersionDesc(contractId)
                            .orElseThrow(() -> new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE));
                    hash = submissionService.escrowRevisionHash(contractId, latest.getId(), input.review());
                }
            }
            case "claim" -> {
                if (client || !"Submitted".equals(chain.status())
                        || chain.reviewDueAt() == null
                        || Instant.now().isBefore(Instant.ofEpochSecond(Long.parseLong(chain.reviewDueAt())))) {
                    throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
                }
            }
            case "review-dispute" -> {
                if (!client || !"Submitted".equals(chain.status())
                        || input == null || input.review() == null
                        || input.review().getDecision() != ReviewSubmissionRequest.Decision.OPEN_DISPUTE) {
                    throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
                }
                JobSubmission latest = submissions.findFirstByContractIdOrderByVersionDesc(contractId)
                        .orElseThrow(() -> new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE));
                submissionService.validateEscrowReviewDraft(contractId, input.review());
                hash = submissionService.escrowDisputeHash(contractId, latest.getId(), input.review());
                gatewayAction = "open-dispute";
            }
            case "open-dispute" -> {
                if (input == null || input.reasonCode() == null || input.reasonCode().isBlank()
                        || input.reasonCode().length() > 60 || input.description() == null
                        || input.description().isBlank() || input.description().length() > 2000
                        || !("Funded".equals(chain.status()) || "Submitted".equals(chain.status())
                            || "Revision".equals(chain.status()))) {
                    throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
                }
                if ("Funded".equals(chain.status()) && (chain.deliveryDueAt() == null
                        || !Instant.now().isAfter(Instant.ofEpochSecond(
                            Long.parseLong(chain.deliveryDueAt()))))) {
                    throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
                }
                JobSubmission latest = submissions.findFirstByContractIdOrderByVersionDesc(contractId)
                        .orElse(null);
                hash = disputeService.escrowDisputeHash(contractId,
                        latest == null ? null : latest.getId(),
                        input.reasonCode(), input.description());
            }
            case "request-extension" -> {
                if (client || !"Funded".equals(chain.status()) || input == null
                        || input.newDueAt() == null) throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
                newDue = input.newDueAt();
            }
            case "approve-extension" -> {
                if (!client || !"Funded".equals(chain.status())) {
                    throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
                }
            }
            default -> throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("actor", actorWallet);
        request.put("mode", "build");
        if (hash != null) request.put("hash", hash);
        if (newDue != null) request.put("newDueAt", newDue);
        SolanaBuildResult built = solana.buildEscrowAction(record.getMilestoneId().toString(),
                gatewayAction, request);
        if (built.requiredSigners() == null || !built.requiredSigners().contains(actorWallet)) {
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        }
        EscrowActionIntent intent = new EscrowActionIntent();
        intent.setContractId(contractId);
        intent.setMilestoneId(record.getMilestoneId());
        intent.setActorId(actorId);
        intent.setAction(action);
        intent.setBuildSessionId(built.buildSessionId());
        intent.setPayloadHash(hash);
        try {
            if (input != null) intent.setPayloadJson(objectMapper.writeValueAsString(input));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
        intents.saveAndFlush(intent);
        return new BuildView(intent.getId(), action, built.buildSessionId(),
                built.transactionBase64(), record.getEscrowAddress(), actorWallet,
                chain.reviewDueAt(), hash);
    }

    @Transactional
    public ActionStatus submit(UUID actorId, UUID contractId, UUID intentId, SignedInput input) {
        participant(actorId, contractId);
        EscrowActionIntent intent = intents.findById(intentId)
                .filter(i -> i.getContractId().equals(contractId) && i.getActorId().equals(actorId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE));
        if (input == null || input.transactionBase64() == null || input.transactionBase64().isBlank()) {
            throw new ApplicationException(ErrorCode.INVALID_DATA);
        }
        if (intent.getSignature() == null) {
            intent.setSignature(solana.submitEscrowSigned(intent.getBuildSessionId(),
                    input.transactionBase64()));
            intents.saveAndFlush(intent);
            if ("release".equals(intent.getAction()) || "claim".equals(intent.getAction())) {
                escrows.findByContractId(contractId).ifPresent(record ->
                        {
                            record.setReleaseSignature(intent.getSignature());
                            record.setReleaseSubmittedAt(Instant.now());
                            record.setSettlementRetryPending(false);
                        });
            }
        }
        String chainStatus = solana.findEscrow(intent.getMilestoneId().toString())
                .map(SolanaEscrowResult::status).orElse("UNKNOWN");
        return new ActionStatus(intent.getId(), intent.getAction(), intent.getSignature(), chainStatus);
    }

    @Transactional
    public MutualRefundView prepareMutualRefund(UUID actorId, UUID contractId) {
        WorkContract contract = participant(actorId, contractId);
        if (!actorId.equals(contract.getClientUserId()))
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        EscrowContract record = escrows.findByContractId(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE));
        SolanaEscrowResult chain = solana.findEscrow(record.getMilestoneId().toString())
                .orElseThrow(() -> new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE));
        if (!chain.address().equals(record.getEscrowAddress())
                || !("Funded".equals(chain.status()) || "Submitted".equals(chain.status())
                    || "Revision".equals(chain.status())))
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        SolanaBuildResult built = solana.buildEscrowMutualRefund(record.getMilestoneId().toString(),
                Map.of("client", record.getClientWallet(), "freelancer", record.getFreelancerWallet(),
                        "mode", "build"));
        if (built.requiredSigners() == null
                || !built.requiredSigners().contains(record.getClientWallet())
                || !built.requiredSigners().contains(record.getFreelancerWallet()))
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        EscrowActionIntent intent = new EscrowActionIntent();
        intent.setContractId(contractId); intent.setMilestoneId(record.getMilestoneId());
        intent.setActorId(actorId); intent.setAction("mutual-refund");
        intent.setBuildSessionId(built.buildSessionId());
        intent.setOriginalTransaction(built.transactionBase64());
        intents.saveAndFlush(intent);
        return mutualView(intent, record);
    }

    @Transactional
    public MutualRefundView clientSignMutualRefund(UUID actorId, UUID contractId,
            UUID intentId, SignedInput input) {
        WorkContract contract = participant(actorId, contractId);
        if (!actorId.equals(contract.getClientUserId()) || input == null
                || input.transactionBase64() == null || input.transactionBase64().isBlank())
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        EscrowActionIntent intent = mutualIntent(contractId, intentId);
        if (!intent.getActorId().equals(actorId) || intent.getSignature() != null)
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        if (intent.getPartialTransaction() != null
                && !intent.getPartialTransaction().equals(input.transactionBase64()))
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        intent.setPartialTransaction(input.transactionBase64());
        EscrowContract record = escrows.findByContractId(contractId).orElseThrow();
        return mutualView(intent, record);
    }

    @Transactional(readOnly = true)
    public MutualRefundView pendingMutualRefund(UUID actorId, UUID contractId) {
        WorkContract contract = participant(actorId, contractId);
        if (!actorId.equals(contract.getFreelancerId()))
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        EscrowContract record = escrows.findByContractId(contractId).orElseThrow();
        return intents.findFirstByContractIdAndActionAndPartialTransactionIsNotNullAndSignatureIsNullOrderByCreatedAtDesc(
                contractId, "mutual-refund").map(intent -> mutualView(intent, record)).orElse(null);
    }

    @Transactional
    public ActionStatus finishMutualRefund(UUID actorId, UUID contractId,
            UUID intentId, SignedInput input) {
        WorkContract contract = participant(actorId, contractId);
        if (!actorId.equals(contract.getFreelancerId()) || input == null
                || input.transactionBase64() == null || input.transactionBase64().isBlank())
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        EscrowActionIntent intent = mutualIntent(contractId, intentId);
        if (intent.getPartialTransaction() == null)
            throw new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE);
        if (intent.getSignature() == null) {
            intent.setSignature(solana.submitEscrowSigned(intent.getBuildSessionId(),
                    input.transactionBase64()));
            escrows.findByContractId(contractId).ifPresent(record ->
                    {
                        record.setRefundSignature(intent.getSignature());
                        record.setRefundSubmittedAt(Instant.now());
                    });
        }
        String chainStatus = solana.findEscrow(intent.getMilestoneId().toString())
                .map(SolanaEscrowResult::status).orElse("UNKNOWN");
        return new ActionStatus(intent.getId(), intent.getAction(), intent.getSignature(), chainStatus);
    }

    private EscrowActionIntent mutualIntent(UUID contractId, UUID intentId) {
        return intents.findWithLockById(intentId)
                .filter(intent -> intent.getContractId().equals(contractId)
                        && "mutual-refund".equals(intent.getAction()))
                .orElseThrow(() -> new ApplicationException(ErrorCode.SUBMISSION_INVALID_STATE));
    }

    private MutualRefundView mutualView(EscrowActionIntent intent, EscrowContract record) {
        return new MutualRefundView(intent.getId(), intent.getBuildSessionId(),
                intent.getOriginalTransaction(), intent.getPartialTransaction(),
                record.getClientWallet(), record.getFreelancerWallet());
    }

    private WorkContract participant(UUID actorId, UUID contractId) {
        return contracts.findById(contractId)
                .filter(c -> c.getClientUserId().equals(actorId) || c.getFreelancerId().equals(actorId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.CONTRACT_SUBMISSION_NOT_FOUND));
    }
}
