package com.marketplace.backend.service.impl;

import com.marketplace.backend.blockchain.BlockchainGateway;
import com.marketplace.backend.blockchain.BlockchainGatewayRegistry;
import com.marketplace.backend.blockchain.EnvironmentInfo;
import com.marketplace.backend.blockchain.EscrowActionRequest;
import com.marketplace.backend.blockchain.EscrowReference;
import com.marketplace.backend.blockchain.EscrowSnapshot;
import com.marketplace.backend.blockchain.IssuerAlert;
import com.marketplace.backend.blockchain.NeutralEscrowStatus;
import com.marketplace.backend.blockchain.PreparedAction;
import com.marketplace.backend.blockchain.TransactionReference;
import com.marketplace.backend.blockchain.TransactionResult;
import com.marketplace.backend.blockchain.TransactionStatus;
import com.marketplace.backend.blockchain.avalanche.AvalancheAction;
import com.marketplace.backend.blockchain.avalanche.AvalancheErrorClass;
import com.marketplace.backend.blockchain.avalanche.AvalancheEscrowIds;
import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.dto.request.avalanche.AvalancheActionInput;
import com.marketplace.backend.dto.response.avalanche.AvalancheContractView;
import com.marketplace.backend.dto.response.avalanche.MutualRefundState;
import com.marketplace.backend.dto.response.avalanche.MutualSettlementState;
import com.marketplace.backend.entity.AvalancheChainTransaction;
import com.marketplace.backend.entity.AvalanchePendingAuthorization;
import com.marketplace.backend.entity.ContractStatus;
import com.marketplace.backend.entity.DisputeStatus;
import com.marketplace.backend.entity.JobStatus;
import com.marketplace.backend.entity.Milestone;
import com.marketplace.backend.entity.MilestoneStatus;
import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.AvalancheGatewayException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.AvalancheChainTransactionRepository;
import com.marketplace.backend.repository.AvalanchePendingAuthorizationRepository;
import com.marketplace.backend.repository.ContractDisputeRepository;
import com.marketplace.backend.repository.EscrowContractRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import com.marketplace.backend.service.AvalancheEscrowService;
import com.marketplace.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.marketplace.backend.service.impl.AvalancheActionAuthorizer.UNRESOLVED_DISPUTE;
import static com.marketplace.backend.service.impl.AvalancheActionAuthorizer.baseUnits;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class AvalancheEscrowServiceImpl implements AvalancheEscrowService {

    private static final Pattern SIGNATURE_HEX = Pattern.compile("0x[0-9a-fA-F]{130}");
    private static final Pattern DIGEST_HEX = Pattern.compile("0x[0-9a-fA-F]{64}");
    private static final Pattern TX_HASH = Pattern.compile("0x[0-9a-fA-F]{64}");
    private static final String NOT_ENABLED_NOTICE = "Hợp đồng này chưa dùng ký quỹ Avalanche.";
    private static final String DEMONSTRATION_NOTICE = "Demonstration only: ký quỹ mô phỏng, USDC testnet hoặc mô phỏng, không có giá trị thực.";

    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final EscrowContractRepository solanaEscrows;
    private final AvalancheChainTransactionRepository chainTransactions;
    private final ContractDisputeRepository disputes;
    private final BlockchainGatewayRegistry gateways;
    private final AvalancheGatewayProperties properties;
    /** F13: first signature of a two-party authorization, held until the second party signs the same typed data (V7). */
    private final AvalanchePendingAuthorizationRepository pendingAuthorizations;
    /** Phase 3 D-B (notifications only; fees, tax and payout are not wired): used only when settlement-notifications is on. */
    private final NotificationService notifications;
    /** A2 finding: the job follows the contract as on the Solana escrow rail (fund IN_PROGRESS, release COMPLETED, refund CANCELLED). */
    private final JobRepository jobs;
    private final AvalancheActionAuthorizer authorizer;

    /** What a finalized transaction did to the business state. */
    private enum Effect { NONE, MISMATCH, APPLIED }

    @Override
    @Transactional
    public AvalancheContractView enable(UUID userId, UUID contractId) {
        WorkContract contract = contract(contractId);
        if (!authorizer.isClient(contract, userId)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        if (!railMayStillBeChosen(contract)) {
            throw invalid("the Avalanche rail can only be chosen before funding and never for a contract that has a Solana escrow");
        }
        contract.setPaymentNetworkId(properties.getNetwork());
        contracts.save(contract);
        return view(userId, false, contract);
    }

    @Override
    @Transactional(readOnly = true)
    public AvalancheContractView status(UUID userId, boolean admin, UUID contractId) {
        WorkContract contract = contract(contractId);
        authorizer.requireParticipantOrAdmin(contract, userId, admin);
        return view(userId, admin, contract);
    }

    @Override
    @Transactional(readOnly = true)
    public PreparedAction prepare(UUID userId, boolean admin, UUID contractId, String action, AvalancheActionInput input) {
        WorkContract contract = contract(contractId);
        Map<String, Object> params = authorizer.authorize(userId, admin, contract, action, orEmpty(input));
        String escrowId = AvalancheEscrowIds.fromMilestone(authorizer.milestone(contractId).getId());
        return gateway().prepareAction(new EscrowActionRequest(escrowId, action, null, params));
    }

    @Override
    @Transactional
    public AvalancheChainTransaction execute(UUID userId, boolean admin, UUID contractId, String action, AvalancheActionInput in) {
        AvalancheActionInput input = orEmpty(in);
        WorkContract contract = contract(contractId);
        Map<String, Object> params = authorizer.authorize(userId, admin, contract, action, input);
        Milestone milestone = authorizer.milestone(contractId);
        String escrowId = AvalancheEscrowIds.fromMilestone(milestone.getId());
        String operationKey = contractId + ":" + action + ":" + idempotencySuffix(input);
        AvalancheChainTransaction existing = chainTransactions.findByNetworkIdAndOperationKey(properties.getNetwork(), operationKey).orElse(null);
        if (existing != null) {
            return existing;
        }
        AvalancheChainTransaction row = newRow(userId, contractId, milestone.getId(), escrowId, action, operationKey, gateway().environmentInfo());
        chainTransactions.saveAndFlush(row);
        try {
            apply(row, gateway().executeAction(new EscrowActionRequest(escrowId, action, operationKey, params)));
        } catch (AvalancheGatewayException e) {
            row.setStatus((e.isDefinitive() ? TransactionStatus.FAILED : TransactionStatus.UNKNOWN).name());
            row.setErrorClass(e.getCode() != null ? e.getCode()
                    : (e.isDefinitive() ? AvalancheErrorClass.GATEWAY_REJECTED : AvalancheErrorClass.GATEWAY_UNREACHABLE).name());
        }
        row.setUpdatedAt(Instant.now());
        return chainTransactions.save(row);
    }

    @Override
    @Transactional
    public AvalancheChainTransaction recordUserTransaction(UUID userId, boolean admin, UUID contractId, String action, String txHash,
                                                           AvalancheActionInput in) {
        WorkContract contract = contract(contractId);
        authorizer.authorize(userId, admin, contract, action, orEmpty(in));
        if (txHash == null || !TX_HASH.matcher(txHash).matches()) {
            throw invalid("a 32-byte transaction hash is required");
        }
        String key = contractId + ":" + action + ":" + txHash.toLowerCase(Locale.ROOT);
        AvalancheChainTransaction existing = chainTransactions.findByNetworkIdAndOperationKey(properties.getNetwork(), key).orElse(null);
        if (existing != null) {
            return existing;
        }
        Milestone milestone = authorizer.milestone(contractId);
        AvalancheChainTransaction row = newRow(userId, contractId, milestone.getId(), AvalancheEscrowIds.fromMilestone(milestone.getId()), action,
                key, gateway().environmentInfo());
        row.setTxHash(txHash.toLowerCase(Locale.ROOT));
        row.setStatus(TransactionStatus.SUBMITTED.name());
        return chainTransactions.save(row);
    }

    @Override
    @Transactional
    public AvalancheContractView refresh(UUID userId, boolean admin, UUID contractId) {
        WorkContract contract = contract(contractId);
        authorizer.requireParticipantOrAdmin(contract, userId, admin);
        authorizer.requireAvalanche(contract);
        for (AvalancheChainTransaction row : chainTransactions.findByContractIdOrderByIdAsc(contractId)) {
            if (!TransactionStatus.valueOf(row.getStatus()).isTerminal()) {
                refreshFromGateway(row);
            }
            if (TransactionStatus.FINALIZED.name().equals(row.getStatus()) && !row.isBusinessApplied()) {
                applyBusinessEffect(contract, row);
            }
            chainTransactions.save(row);
        }
        return view(userId, admin, contract);
    }

    @Override
    @Transactional
    public AvalancheContractView submitMutualRefundSignature(UUID userId, UUID contractId, Long authorizationDeadline, String typedDataDigest,
                                                             String signature) {
        WorkContract contract = contract(contractId);
        authorizer.requireAvalanche(contract);
        require(authorizer.isClient(contract, userId) || authorizer.isFreelancer(contract, userId), "only a party takes part in a mutual refund");
        Map<String, Object> params = new HashMap<>();
        params.put("authorizationDeadline", authorizationDeadline);
        return holdPartySignature(userId, contract, AvalancheAction.MUTUAL_REFUND, AvalancheAction.MUTUAL_REFUND.pendingKey(false), params,
                authorizationDeadline, typedDataDigest, signature);
    }

    @Override
    @Transactional
    public AvalancheContractView submitMutualSettlementSignature(UUID userId, UUID contractId, UUID disputeId, Boolean releaseToFreelancer,
                                                                 Long authorizationDeadline, String typedDataDigest, String signature) {
        WorkContract contract = contract(contractId);
        AvalancheActionInput proposal = new AvalancheActionInput(null, null, disputeId, null, null, null, null, authorizationDeadline,
                releaseToFreelancer);
        Map<String, Object> params = authorizer.authorize(userId, false, contract, AvalancheAction.MUTUAL_SETTLE.value(), proposal);
        return holdPartySignature(userId, contract, AvalancheAction.MUTUAL_SETTLE, AvalancheAction.MUTUAL_SETTLE.pendingKey(releaseToFreelancer),
                params, authorizationDeadline, typedDataDigest, signature);
    }

    private AvalancheContractView holdPartySignature(UUID userId, WorkContract contract, AvalancheAction action, String key,
                                                     Map<String, Object> params, Long authorizationDeadline, String typedDataDigest,
                                                     String signature) {
        UUID contractId = contract.getId();
        require(authorizationDeadline != null && authorizationDeadline > Instant.now().getEpochSecond(), "the authorization has expired");
        require(typedDataDigest != null && DIGEST_HEX.matcher(typedDataDigest).matches(), "a 32-byte typed-data digest is required");
        require(signature != null && SIGNATURE_HEX.matcher(signature).matches(), "a 65-byte signature is required");
        String escrowId = AvalancheEscrowIds.fromMilestone(authorizer.milestone(contractId).getId());
        PreparedAction expected = gateway().prepareAction(new EscrowActionRequest(escrowId, action.value(), null, params));
        require(typedDataDigest.equalsIgnoreCase(expected.typedDataDigest()), "the signed data does not match the current escrow authorization");

        AvalanchePendingAuthorization pending = pendingAuthorizations.findWithLockByContractIdAndAction(contractId, key)
                .orElseGet(() -> newPending(contractId, key));
        boolean differentProposal = pending.getTypedDataDigest() == null || pending.getAuthorizationDeadline() != authorizationDeadline
                || !pending.getTypedDataDigest().equalsIgnoreCase(typedDataDigest);
        if (differentProposal) {
            pending.setAuthorizationDeadline(authorizationDeadline);
            pending.setTypedDataDigest(typedDataDigest.toLowerCase(Locale.ROOT));
            pending.setClientSignature(null);
            pending.setFreelancerSignature(null);
        }
        if (authorizer.isClient(contract, userId)) {
            pending.setClientSignature(signature.toLowerCase(Locale.ROOT));
        } else {
            pending.setFreelancerSignature(signature.toLowerCase(Locale.ROOT));
        }
        pending.setUpdatedAt(Instant.now());
        pendingAuthorizations.saveAndFlush(pending);
        return view(userId, false, contract);
    }

    private static AvalanchePendingAuthorization newPending(UUID contractId, String key) {
        AvalanchePendingAuthorization pending = new AvalanchePendingAuthorization();
        pending.setContractId(contractId);
        pending.setAction(key);
        pending.setCreatedAt(Instant.now());
        return pending;
    }

    private AvalancheContractView view(UUID userId, boolean admin, WorkContract contract) {
        if (!properties.getNetwork().equals(contract.getPaymentNetworkId())) {
            return notEnabledView(contract);
        }
        String escrowId = AvalancheEscrowIds.fromMilestone(authorizer.milestone(contract.getId()).getId());
        EnvironmentInfo environment = gateway().environmentInfo();
        EscrowSnapshot snapshot = gateway().escrowSnapshot(new EscrowReference(escrowId));
        return new AvalancheContractView(contract.getPaymentNetworkId(), true, environment, escrowId, snapshot,
                chainTransactions.findByContractIdOrderByIdAsc(contract.getId()), DEMONSTRATION_NOTICE,
                mutualRefundState(contract.getId()), snapshot == null ? List.of() : issuerAlerts(escrowId),
                mutualSettlementStates(contract.getId()));
    }

    /**
     * A2/B7 finding: the client can choose the rail in the UI only if the view says the rail is available. It is offered only
     * while the choice is still valid (same conditions as {@link #enable}): awaiting funding, no Solana escrow, no other network.
     */
    private AvalancheContractView notEnabledView(WorkContract contract) {
        boolean canChoose = contract.getStatus() == ContractStatus.PENDING_FUNDING && contract.getPaymentNetworkId() == null
                && solanaEscrows.findByContractId(contract.getId()).isEmpty();
        EnvironmentInfo environment = canChoose ? environmentOrNull() : null;
        return new AvalancheContractView(null, canChoose, environment, null, null, List.of(), NOT_ENABLED_NOTICE);
    }

    /** Shown without the environment banner when the gateway cannot answer. */
    private EnvironmentInfo environmentOrNull() {
        try {
            return gateway().environmentInfo();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean railMayStillBeChosen(WorkContract contract) {
        boolean otherNetworkChosen = contract.getPaymentNetworkId() != null && !properties.getNetwork().equals(contract.getPaymentNetworkId());
        return contract.getStatus() == ContractStatus.PENDING_FUNDING
                && solanaEscrows.findByContractId(contract.getId()).isEmpty()
                && !otherNetworkChosen;
    }

    private void refreshFromGateway(AvalancheChainTransaction row) {
        String reference = row.getTxHash() != null && row.getOperationKey().endsWith(row.getTxHash()) ? row.getTxHash() : row.getOperationKey();
        try {
            apply(row, gateway().transactionStatus(new TransactionReference(gateway().network(), reference)));
        } catch (AvalancheGatewayException e) {
            row.setErrorClass(e.getCode() != null ? e.getCode() : AvalancheErrorClass.GATEWAY_UNREACHABLE.name());
        }
        row.setUpdatedAt(Instant.now());
    }

    /** P3-C4: a failed report never breaks the view; null means "not known right now", an empty list means "nothing blocks". */
    private List<IssuerAlert> issuerAlerts(String escrowId) {
        try {
            return gateway().issuerAlerts(new EscrowReference(escrowId));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<MutualSettlementState> mutualSettlementStates(UUID contractId) {
        List<MutualSettlementState> states = new ArrayList<>();
        for (boolean release : new boolean[]{true, false}) {
            pendingAuthorizations.findByContractIdAndAction(contractId, AvalancheAction.MUTUAL_SETTLE.pendingKey(release))
                    .filter(a -> a.getAuthorizationDeadline() > Instant.now().getEpochSecond())
                    .ifPresent(a -> states.add(new MutualSettlementState(release, a.getAuthorizationDeadline(), a.getTypedDataDigest(),
                            a.getClientSignature() != null, a.getFreelancerSignature() != null)));
        }
        return states;
    }

    private MutualRefundState mutualRefundState(UUID contractId) {
        return pendingAuthorizations.findByContractIdAndAction(contractId, AvalancheAction.MUTUAL_REFUND.pendingKey(false))
                .filter(a -> a.getAuthorizationDeadline() > Instant.now().getEpochSecond())
                .map(a -> new MutualRefundState(a.getAuthorizationDeadline(), a.getTypedDataDigest(),
                        a.getClientSignature() != null, a.getFreelancerSignature() != null))
                .orElse(null);
    }

    /** Business state moves only after the chain state, read independently, agrees with the recorded terms. */
    private void applyBusinessEffect(WorkContract contract, AvalancheChainTransaction row) {
        EscrowSnapshot chain = gateway().escrowSnapshot(new EscrowReference(row.getEscrowId()));
        Milestone milestone = authorizer.milestone(contract.getId());
        NeutralEscrowStatus status = chain == null ? NeutralEscrowStatus.NONE : chain.status();
        Optional<AvalancheAction> action = AvalancheAction.parse(row.getAction());
        Effect effect = action.map(a -> effectOf(a, contract, milestone, chain, status)).orElse(Effect.NONE);
        if (effect == Effect.NONE) {
            row.setBusinessApplied(true);
            return;
        }
        if (effect == Effect.MISMATCH) {
            row.setErrorClass(AvalancheErrorClass.CHAIN_MISMATCH.name());
            return;
        }
        milestones.save(milestone);
        contracts.save(contract);
        updateJob(contract, milestone.getStatus());
        row.setBusinessApplied(true);
        row.setErrorClass(null);
        notifySettlement(contract, row.getAction(), milestone.getStatus());
    }

    private Effect effectOf(AvalancheAction action, WorkContract contract, Milestone milestone, EscrowSnapshot chain, NeutralEscrowStatus status) {
        return switch (action) {
            case FUND -> applyFunding(contract, milestone, chain, status);
            // anchored only when the chain reads Disputed (the administrator decision requires this applied row)
            case DISPUTE -> status == NeutralEscrowStatus.DISPUTED ? Effect.APPLIED : Effect.MISMATCH;
            case RELEASE, RESOLVE -> applyRelease(action, contract, milestone, status);
            case MUTUAL_REFUND -> applyMutualRefund(contract, milestone, status);
            case MUTUAL_SETTLE -> applyMutualSettlement(contract, milestone, status);
            default -> Effect.NONE;
        };
    }

    private Effect applyFunding(WorkContract contract, Milestone milestone, EscrowSnapshot chain, NeutralEscrowStatus status) {
        boolean consistent = status == NeutralEscrowStatus.FUNDED && chain.amountBaseUnits().equals(baseUnits(contract.getBudgetUsd()))
                && chain.client().equalsIgnoreCase(authorizer.walletAddress(contract.getClientUserId()))
                && chain.freelancer().equalsIgnoreCase(authorizer.walletAddress(contract.getFreelancerId()));
        if (!consistent) {
            return Effect.MISMATCH;
        }
        if (milestone.getStatus() == MilestoneStatus.PENDING_FUNDING) {
            milestone.setStatus(MilestoneStatus.FUNDED);
            contract.setStatus(ContractStatus.ACTIVE);
        }
        return Effect.APPLIED;
    }

    private Effect applyRelease(AvalancheAction action, WorkContract contract, Milestone milestone, NeutralEscrowStatus status) {
        if (status == NeutralEscrowStatus.RELEASED) {
            milestone.setStatus(MilestoneStatus.RELEASED);
            contract.setStatus(ContractStatus.COMPLETED);
        } else if (status == NeutralEscrowStatus.REFUNDED && action == AvalancheAction.RESOLVE) {
            milestone.setStatus(MilestoneStatus.REFUNDED);
            contract.setStatus(ContractStatus.CANCELLED);
        } else {
            return Effect.MISMATCH;
        }
        if (action == AvalancheAction.RESOLVE) {
            closeUnresolvedDispute(contract, status == NeutralEscrowStatus.RELEASED);
        }
        return Effect.APPLIED;
    }

    private Effect applyMutualRefund(WorkContract contract, Milestone milestone, NeutralEscrowStatus status) {
        if (status != NeutralEscrowStatus.REFUNDED) {
            return Effect.MISMATCH;
        }
        milestone.setStatus(MilestoneStatus.REFUNDED);
        contract.setStatus(ContractStatus.CANCELLED);
        pendingAuthorizations.findByContractIdAndAction(contract.getId(), AvalancheAction.MUTUAL_REFUND.pendingKey(false))
                .ifPresent(pendingAuthorizations::delete);
        return Effect.APPLIED;
    }

    /** R12: the chain outcome decides; the recorded dispute follows it (existing RESOLVED_* values). */
    private Effect applyMutualSettlement(WorkContract contract, Milestone milestone, NeutralEscrowStatus status) {
        boolean released = status == NeutralEscrowStatus.RELEASED;
        if (!released && status != NeutralEscrowStatus.REFUNDED) {
            return Effect.MISMATCH;
        }
        milestone.setStatus(released ? MilestoneStatus.RELEASED : MilestoneStatus.REFUNDED);
        contract.setStatus(released ? ContractStatus.COMPLETED : ContractStatus.CANCELLED);
        closeUnresolvedDispute(contract, released);
        for (boolean release : new boolean[]{true, false}) {
            pendingAuthorizations.findByContractIdAndAction(contract.getId(), AvalancheAction.MUTUAL_SETTLE.pendingKey(release))
                    .ifPresent(pendingAuthorizations::delete);
        }
        return Effect.APPLIED;
    }

    /** The recorded dispute follows the chain outcome (existing RESOLVED_* values). */
    private void closeUnresolvedDispute(WorkContract contract, boolean released) {
        disputes.findFirstByContractIdAndStatusInOrderByCreatedAtDesc(contract.getId(), UNRESOLVED_DISPUTE)
                .ifPresent(d -> {
                    d.setStatus(released ? DisputeStatus.RESOLVED_RELEASE : DisputeStatus.RESOLVED_REFUND);
                    d.setResolvedAt(Instant.now());
                    disputes.save(d);
                });
    }

    /** Mirrors SolanaEscrowFundingService / SolanaEscrowTimeoutService: the job status follows an applied escrow outcome. */
    private void updateJob(WorkContract contract, MilestoneStatus result) {
        if (jobs == null || contract.getJobId() == null) {
            return;
        }
        jobs.findById(contract.getJobId()).ifPresent(job -> {
            JobStatus before = job.getStatus();
            if (result == MilestoneStatus.FUNDED && before == JobStatus.AWAITING_PAYMENT) {
                job.setStatus(JobStatus.IN_PROGRESS);
            } else if (result == MilestoneStatus.RELEASED) {
                job.setStatus(JobStatus.COMPLETED);
            } else if (result == MilestoneStatus.REFUNDED) {
                job.setStatus(JobStatus.CANCELLED);
            }
            if (job.getStatus() != before) {
                jobs.save(job);
            }
        });
    }

    /**
     * Phase 3 D-B, notifications slice: tells both parties that an Avalanche escrow step took effect, once, at the moment the
     * business effect is applied (FINALIZED and an agreeing chain read). Wording follows SIMULATION_SCOPE: a simulated escrow,
     * testnet or simulated USDC, no real funds. Off by default ({@code avalanche-gateway.settlement-notifications}).
     */
    private void notifySettlement(WorkContract contract, String action, MilestoneStatus result) {
        if (!properties.isSettlementNotifications() || notifications == null) {
            return;
        }
        NotificationType type;
        String title;
        if (AvalancheAction.FUND.value().equals(action)) {
            type = NotificationType.FUNDING_CONFIRMED;
            title = "Ký quỹ Avalanche (mô phỏng) đã được xác nhận";
        } else if (result == MilestoneStatus.RELEASED) {
            type = NotificationType.RELEASE_CONFIRMED;
            title = "Ký quỹ Avalanche (mô phỏng) đã giải ngân cho Freelancer";
        } else if (result == MilestoneStatus.REFUNDED) {
            type = NotificationType.REFUND_CONFIRMED;
            title = "Ký quỹ Avalanche (mô phỏng) đã hoàn cho Client";
        } else {
            return;
        }
        String message = "Giao dịch đã chốt trên chuỗi. Demonstration only: USDC mô phỏng hoặc testnet, không có tiền thật.";
        for (UUID party : List.of(contract.getClientUserId(), contract.getFreelancerId())) {
            notifications.notify(party, type, title, message, contract.getJobId(), contract.getBudgetUsd());
        }
    }

    private AvalancheChainTransaction newRow(UUID userId, UUID contractId, UUID milestoneId, String escrowId, String action, String key,
                                             EnvironmentInfo environment) {
        AvalancheChainTransaction row = new AvalancheChainTransaction();
        row.setContractId(contractId);
        row.setMilestoneId(milestoneId);
        row.setNetworkId(properties.getNetwork());
        row.setEnvironment(environment.environment());
        row.setChainId(environment.chainId());
        row.setEscrowId(escrowId);
        row.setAction(action);
        row.setOperationKey(key);
        row.setActorUserId(userId);
        row.setStatus(TransactionStatus.CREATED.name());
        row.setCreatedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        return row;
    }

    private static void apply(AvalancheChainTransaction row, TransactionResult result) {
        row.setStatus(result.status().name());
        row.setTxHash(result.transactionHash() != null ? result.transactionHash() : row.getTxHash());
        row.setErrorClass(result.errorClass());
    }

    private static String idempotencySuffix(AvalancheActionInput input) {
        boolean given = input.idempotencyKey() != null && !input.idempotencyKey().isBlank();
        return given ? input.idempotencyKey() : UUID.randomUUID().toString();
    }

    private static AvalancheActionInput orEmpty(AvalancheActionInput input) {
        return input == null ? AvalancheActionInput.empty() : input;
    }

    private BlockchainGateway gateway() {
        return gateways.require(properties.getNetwork());
    }

    private WorkContract contract(UUID contractId) {
        return contracts.findById(contractId).orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "contract " + contractId));
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
