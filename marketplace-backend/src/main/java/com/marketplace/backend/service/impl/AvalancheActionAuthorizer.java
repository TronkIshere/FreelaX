package com.marketplace.backend.service.impl;

import com.marketplace.backend.blockchain.BlockchainAddress;
import com.marketplace.backend.blockchain.BlockchainGatewayRegistry;
import com.marketplace.backend.blockchain.avalanche.AvalancheAction;
import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.dto.request.avalanche.AvalancheActionInput;
import com.marketplace.backend.entity.ContractDispute;
import com.marketplace.backend.entity.ContractStatus;
import com.marketplace.backend.entity.DisputeStatus;
import com.marketplace.backend.entity.EvmWallet;
import com.marketplace.backend.entity.JobSubmission;
import com.marketplace.backend.entity.JobSubmissionStatus;
import com.marketplace.backend.entity.Milestone;
import com.marketplace.backend.entity.PaymentFlow;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.AvalanchePendingAuthorizationRepository;
import com.marketplace.backend.repository.ContractDisputeRepository;
import com.marketplace.backend.repository.EvmWalletRepository;
import com.marketplace.backend.repository.JobSubmissionRepository;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authorization of the Avalanche rail, enforced BEFORE anything reaches the gateway: who may do what, with terms taken only from
 * recorded contract, milestone and wallet rows, and evidence, feedback and dispute hashes derived only from recorded rows.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class AvalancheActionAuthorizer {

    public static final Set<DisputeStatus> UNRESOLVED_DISPUTE = Set.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW,
            DisputeStatus.DECISION_PENDING_RELEASE, DisputeStatus.DECISION_PENDING_REFUND);

    private static final Duration FUNDING_WINDOW = Duration.ofHours(48);
    private static final int USDC_DECIMALS = 6;

    private final MilestoneRepository milestones;
    private final WalletRepository wallets;
    private final JobSubmissionRepository submissions;
    private final ContractDisputeRepository disputes;
    private final BlockchainGatewayRegistry gateways;
    private final AvalancheGatewayProperties properties;
    /** A8: EVM addresses proven by an EIP-191 challenge (V7). Preferred over the legacy `wallets` rows. */
    private final EvmWalletRepository evmWallets;
    /** F13: first signature of a two-party authorization, held until the second party signs the same typed data (V7). */
    private final AvalanchePendingAuthorizationRepository pendingAuthorizations;

    private record Caller(UUID userId, boolean admin, boolean client, boolean freelancer) {
        boolean party() {
            return client || freelancer;
        }
    }

    /** Checks the caller's right to perform the action and builds the gateway parameters from recorded data only. */
    public Map<String, Object> authorize(UUID userId, boolean admin, WorkContract contract, String actionName, AvalancheActionInput in) {
        AvalancheAction action = AvalancheAction.parse(actionName)
                .orElseThrow(() -> invalid("unknown action " + actionName));
        requireAvalanche(contract);
        Caller caller = new Caller(userId, admin, isClient(contract, userId), isFreelancer(contract, userId));
        Map<String, Object> params = new HashMap<>();
        switch (action) {
            case FUND -> authorizeFund(caller, contract, params);
            case SUBMIT -> authorizeSubmit(caller, contract, in, params);
            case REVISE -> authorizeRevision(caller, contract, in, params);
            case EXTENSION_REQUEST -> authorizeExtensionRequest(caller, in, params);
            case EXTENSION_APPROVE -> require(caller.client(), "only the client approves an extension");
            case DISPUTE -> authorizeDispute(caller, contract, in, params);
            case RELEASE -> authorizeRelease(caller, params);
            case RESOLVE -> authorizeResolution(caller, contract, in, params);
            case MUTUAL_REFUND -> authorizeMutualRefund(caller, contract, in, params);
            case MUTUAL_SETTLE -> authorizeMutualSettlement(caller, contract, in, params);
        }
        return params;
    }

    public boolean isClient(WorkContract contract, UUID user) {
        return contract.getClientUserId().equals(user);
    }

    public boolean isFreelancer(WorkContract contract, UUID user) {
        return contract.getFreelancerId().equals(user);
    }

    public void requireParticipantOrAdmin(WorkContract contract, UUID user, boolean admin) {
        if (!admin && !isClient(contract, user) && !isFreelancer(contract, user)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
    }

    public void requireAvalanche(WorkContract contract) {
        if (!properties.getNetwork().equals(contract.getPaymentNetworkId())) {
            throw invalid("this contract does not use the Avalanche rail");
        }
    }

    public Milestone milestone(UUID contractId) {
        return milestones.findByContractId(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "milestone of " + contractId));
    }

    /** The EVM address of a user: the proven link first, then a legacy wallet row that the gateway accepts as an address. */
    public String walletAddress(UUID userId) {
        EvmWallet linked = evmWallets == null ? null : evmWallets.findByUserId(userId).orElse(null);
        if (linked != null) {
            return linked.getAddress().toLowerCase(Locale.ROOT);
        }
        Wallet wallet = wallets.findFirstByUserIdOrderByIdAsc(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.AVALANCHE_WALLET_NOT_LINKED));
        String address = wallet.getPublicKey();
        if (address == null || !gateways.require(properties.getNetwork()).validateAddress(new BlockchainAddress(address))) {
            throw new ApplicationException(ErrorCode.AVALANCHE_WALLET_NOT_LINKED);
        }
        return address.toLowerCase(Locale.ROOT);
    }

    public static String baseUnits(BigDecimal usd) {
        return usd.movePointRight(USDC_DECIMALS).toBigIntegerExact().toString();
    }

    private void authorizeFund(Caller caller, WorkContract contract, Map<String, Object> params) {
        require(caller.client(), "only the client funds");
        require(contract.getStatus() == ContractStatus.PENDING_FUNDING, "the contract is not awaiting funding");
        Milestone milestone = milestone(contract.getId());
        require(contract.getDeliveryDueAt() != null, "a delivery date is required");
        require(milestone.getAmount() != null && milestone.getAmount().compareTo(contract.getBudgetUsd()) == 0,
                "the milestone amount differs from the contract budget");
        require(contract.getReviewWindowHours() == PaymentFlow.REVIEW_WINDOW_HOURS, "the review window differs from the recorded rule");
        params.put("client", walletAddress(contract.getClientUserId()));
        params.put("freelancer", walletAddress(contract.getFreelancerId()));
        params.put("amount", baseUnits(contract.getBudgetUsd()));
        params.put("fundingExpiresAt", Instant.now().plus(FUNDING_WINDOW).getEpochSecond());
        params.put("deliveryDueAt", contract.getDeliveryDueAt().getEpochSecond());
        params.put("reviewWindowHours", contract.getReviewWindowHours());
        params.put("maxRevisions", contract.getMaxRevisions());
        params.put("highValueGrace", false);
    }

    private void authorizeSubmit(Caller caller, WorkContract contract, AvalancheActionInput in, Map<String, Object> params) {
        require(caller.freelancer(), "only the freelancer submits");
        JobSubmission submission = recordedSubmission(contract, in.submissionId());
        require(submission.getStatus() == JobSubmissionStatus.SUBMITTED, "only a submission in review can be anchored");
        params.put("evidenceHash", sha("avalanche-evidence:" + contract.getId() + ":" + submission.getId() + ":" + submission.getPayloadHash()));
    }

    private void authorizeRevision(Caller caller, WorkContract contract, AvalancheActionInput in, Map<String, Object> params) {
        require(caller.client(), "only the client requests a revision");
        JobSubmission submission = recordedSubmission(contract, in.submissionId());
        boolean hasFeedback = submission.getReviewerFeedback() != null && !submission.getReviewerFeedback().isBlank();
        require(submission.getStatus() == JobSubmissionStatus.REVISION_REQUESTED && hasFeedback,
                "a recorded revision request with feedback is required");
        params.put("feedbackHash", sha("avalanche-feedback:" + submission.getId() + ":" + submission.getReviewerFeedback()));
    }

    private void authorizeExtensionRequest(Caller caller, AvalancheActionInput in, Map<String, Object> params) {
        require(caller.freelancer(), "only the freelancer requests an extension");
        require(in.newDeliveryDueAt() != null, "the requested delivery date is required");
        params.put("newDeliveryDueAt", in.newDeliveryDueAt());
    }

    private void authorizeDispute(Caller caller, WorkContract contract, AvalancheActionInput in, Map<String, Object> params) {
        require(caller.party(), "only a party opens a dispute");
        ContractDispute dispute = recordedDispute(contract, in.disputeId());
        require(caller.userId().equals(dispute.getOpenedBy()), "the recorded dispute was opened by someone else");
        params.put("reasonHash", sha("avalanche-dispute:" + dispute.getId() + ":" + dispute.getReasonCode()));
        params.put("by", caller.freelancer() ? "freelancer" : "client");
    }

    private void authorizeRelease(Caller caller, Map<String, Object> params) {
        require(caller.party(), "only a party releases");
        params.put("by", caller.client() ? "client" : "relayer");
    }

    private void authorizeResolution(Caller caller, WorkContract contract, AvalancheActionInput in, Map<String, Object> params) {
        require(caller.admin(), "only the arbiter role (administrator) resolves a dispute");
        require(!caller.party(), "a party cannot act as the arbiter of its own contract");
        ContractDispute dispute = recordedDispute(contract, in.disputeId());
        boolean decided = dispute.getStatus() == DisputeStatus.DECISION_PENDING_RELEASE || dispute.getStatus() == DisputeStatus.DECISION_PENDING_REFUND;
        require(decided, "no recorded administrator decision awaits settlement");
        boolean release = dispute.getStatus() == DisputeStatus.DECISION_PENDING_RELEASE;
        params.put("releaseToFreelancer", release);
        params.put("resolutionHash", sha("avalanche-resolution:" + dispute.getId() + ":" + (release ? "RELEASE" : "REFUND")));
    }

    private void authorizeMutualRefund(Caller caller, WorkContract contract, AvalancheActionInput in, Map<String, Object> params) {
        require(caller.party(), "only a party takes part in a mutual refund");
        putPartySignatures(params, in, contract.getId(), AvalancheAction.MUTUAL_REFUND.pendingKey(false));
    }

    private void authorizeMutualSettlement(Caller caller, WorkContract contract, AvalancheActionInput in, Map<String, Object> params) {
        require(caller.party(), "only a party takes part in a mutual settlement");
        ContractDispute dispute = recordedDispute(contract, in.disputeId());
        require(UNRESOLVED_DISPUTE.contains(dispute.getStatus()), "the recorded dispute is already resolved or cancelled");
        require(in.releaseToFreelancer() != null, "the proposed outcome (releaseToFreelancer) is required");
        boolean release = in.releaseToFreelancer();
        params.put("releaseToFreelancer", release);
        params.put("resolutionHash", settlementHash(dispute, release));
        putPartySignatures(params, in, contract.getId(), AvalancheAction.MUTUAL_SETTLE.pendingKey(release));
    }

    /**
     * Signatures and deadline as given. When none of the three is given, both parties may have signed through the marketplace:
     * the held pair for this proposal and the deadline it covers are used, while that deadline is still in the future.
     */
    private void putPartySignatures(Map<String, Object> params, AvalancheActionInput in, UUID contractId, String pendingKey) {
        if (in.clientSignature() != null) {
            params.put("clientSignature", in.clientSignature());
        }
        if (in.freelancerSignature() != null) {
            params.put("freelancerSignature", in.freelancerSignature());
        }
        if (in.authorizationDeadline() != null) {
            params.put("authorizationDeadline", in.authorizationDeadline());
        }
        boolean nothingGiven = in.clientSignature() == null && in.freelancerSignature() == null && in.authorizationDeadline() == null;
        if (nothingGiven) {
            pendingAuthorizations.findByContractIdAndAction(contractId, pendingKey)
                    .filter(a -> a.getClientSignature() != null && a.getFreelancerSignature() != null)
                    .filter(a -> a.getAuthorizationDeadline() > Instant.now().getEpochSecond())
                    .ifPresent(a -> {
                        params.put("clientSignature", a.getClientSignature());
                        params.put("freelancerSignature", a.getFreelancerSignature());
                        params.put("authorizationDeadline", a.getAuthorizationDeadline());
                    });
        }
    }

    private JobSubmission recordedSubmission(WorkContract contract, UUID submissionId) {
        JobSubmission submission = submissionId == null ? null : submissions.findById(submissionId).orElse(null);
        if (submission == null || !contract.getId().equals(submission.getContractId())) {
            throw invalid("a recorded submission of this contract is required");
        }
        return submission;
    }

    private ContractDispute recordedDispute(WorkContract contract, UUID disputeId) {
        ContractDispute dispute = disputeId == null ? null : disputes.findById(disputeId).orElse(null);
        if (dispute == null || !contract.getId().equals(dispute.getContractId())) {
            throw invalid("a recorded dispute of this contract is required");
        }
        return dispute;
    }

    /** The settlement record both parties sign: the recorded dispute and the proposed outcome, nothing else. */
    private static String settlementHash(ContractDispute dispute, boolean release) {
        return sha("avalanche-settlement:" + dispute.getId() + ":" + (release ? "RELEASE" : "REFUND"));
    }

    private static String sha(String value) {
        try {
            return "0x" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private static ApplicationException invalid(String message) {
        return new ApplicationException(ErrorCode.INVALID_DATA, message);
    }
}
