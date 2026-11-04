package com.marketplace.backend.service;

import com.marketplace.backend.blockchain.BlockchainAddress;
import com.marketplace.backend.blockchain.BlockchainGateway;
import com.marketplace.backend.blockchain.BlockchainGatewayRegistry;
import com.marketplace.backend.blockchain.EnvironmentInfo;
import com.marketplace.backend.blockchain.EscrowActionRequest;
import com.marketplace.backend.blockchain.EscrowReference;
import com.marketplace.backend.blockchain.EscrowSnapshot;
import com.marketplace.backend.blockchain.NeutralEscrowStatus;
import com.marketplace.backend.blockchain.PreparedAction;
import com.marketplace.backend.blockchain.TransactionReference;
import com.marketplace.backend.blockchain.TransactionResult;
import com.marketplace.backend.blockchain.TransactionStatus;
import com.marketplace.backend.blockchain.avalanche.AvalancheEscrowIds;
import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.entity.AvalancheChainTransaction;
import com.marketplace.backend.entity.ContractDispute;
import com.marketplace.backend.entity.ContractStatus;
import com.marketplace.backend.entity.DisputeStatus;
import com.marketplace.backend.entity.JobSubmission;
import com.marketplace.backend.entity.JobSubmissionStatus;
import com.marketplace.backend.entity.Milestone;
import com.marketplace.backend.entity.MilestoneStatus;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.AvalancheGatewayException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.AvalancheChainTransactionRepository;
import com.marketplace.backend.repository.ContractDisputeRepository;
import com.marketplace.backend.repository.EscrowContractRepository;
import com.marketplace.backend.repository.JobSubmissionRepository;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.WalletRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Avalanche rail of a contract. Authorization is enforced HERE, before anything reaches the gateway: who may do what, terms taken
 * only from recorded contract, milestone and wallet rows, evidence, feedback and dispute hashes derived only from recorded rows, and
 * the admin decision bound to the arbiter outcome. Business state changes only when a transaction is FINALIZED and the chain state
 * agrees with the records. A contract that already has a Solana escrow is never moved (in-flight Solana escrows finish on Solana).
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class AvalancheEscrowService {

    private static final Duration FUNDING_WINDOW = Duration.ofHours(48);
    private static final Set<String> ACTIONS = Set.of("fund", "submit", "revise", "extension-request", "extension-approve", "dispute", "release",
            "resolve", "mutual-refund");

    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final WalletRepository wallets;
    private final JobSubmissionRepository submissions;
    private final ContractDisputeRepository disputes;
    private final EscrowContractRepository solanaEscrows;
    private final AvalancheChainTransactionRepository chainTransactions;
    private final BlockchainGatewayRegistry gateways;
    private final AvalancheGatewayProperties properties;
    /** A8: EVM addresses proven by an EIP-191 challenge (V7). Preferred over the legacy `wallets` rows. */
    private final com.marketplace.backend.repository.EvmWalletRepository evmWallets;
    /** F13: first signature of a two-party authorization, held until the second party signs the same typed data (V7). */
    private final com.marketplace.backend.repository.AvalanchePendingAuthorizationRepository pendingAuthorizations;
    /** Phase 3 D-B (notifications only; fees, tax and payout are not wired): used only when settlement-notifications is on. */
    private final NotificationService notifications;
    /** A2 finding: the job follows the contract as on the Solana escrow rail (fund IN_PROGRESS, release COMPLETED, refund CANCELLED). */
    private final com.marketplace.backend.repository.JobRepository jobs;

    static final String MUTUAL_REFUND = "mutual-refund";
    private static final java.util.regex.Pattern SIGNATURE_HEX = java.util.regex.Pattern.compile("0x[0-9a-fA-F]{130}");
    private static final java.util.regex.Pattern DIGEST_HEX = java.util.regex.Pattern.compile("0x[0-9a-fA-F]{64}");

    public record Input(String idempotencyKey, UUID submissionId, UUID disputeId, Long newDeliveryDueAt, String by,
                        String clientSignature, String freelancerSignature, Long authorizationDeadline) {
        public Input(String idempotencyKey, UUID submissionId, UUID disputeId, Long newDeliveryDueAt, String by,
                     String clientSignature, String freelancerSignature) {
            this(idempotencyKey, submissionId, disputeId, newDeliveryDueAt, by, clientSignature, freelancerSignature, null);
        }

        public static Input empty() {
            return new Input(null, null, null, null, null, null, null, null);
        }
    }

    /** F13: progress of a mutual refund signed by both parties' own wallets over the same deadline and digest. */
    public record MutualRefundState(long authorizationDeadline, String typedDataDigest, boolean clientSigned, boolean freelancerSigned) {
    }

    public record ContractView(String networkId, boolean avalancheEnabled, EnvironmentInfo environment, String escrowId, EscrowSnapshot chain,
                               List<AvalancheChainTransaction> transactions, String notice, MutualRefundState mutualRefund) {
        public ContractView(String networkId, boolean avalancheEnabled, EnvironmentInfo environment, String escrowId, EscrowSnapshot chain,
                            List<AvalancheChainTransaction> transactions, String notice) {
            this(networkId, avalancheEnabled, environment, escrowId, chain, transactions, notice, null);
        }
    }

    private BlockchainGateway gateway() {
        return gateways.require(properties.getNetwork());
    }

    private WorkContract contract(UUID contractId) {
        return contracts.findById(contractId).orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "contract " + contractId));
    }

    private static boolean isClient(WorkContract c, UUID user) {
        return c.getClientUserId().equals(user);
    }

    private static boolean isFreelancer(WorkContract c, UUID user) {
        return c.getFreelancerId().equals(user);
    }

    private void requireParticipantOrAdmin(WorkContract c, UUID user, boolean admin) {
        if (!admin && !isClient(c, user) && !isFreelancer(c, user)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void requireAvalanche(WorkContract c) {
        if (!properties.getNetwork().equals(c.getPaymentNetworkId())) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "this contract does not use the Avalanche rail");
        }
    }

    /** Opts a contract into the Avalanche rail. Refused for anything that already moved money or already has a Solana escrow. */
    @Transactional
    public ContractView enable(UUID userId, UUID contractId) {
        WorkContract c = contract(contractId);
        if (!isClient(c, userId)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        if (c.getStatus() != ContractStatus.PENDING_FUNDING || solanaEscrows.findByContractId(contractId).isPresent()
                || (c.getPaymentNetworkId() != null && !properties.getNetwork().equals(c.getPaymentNetworkId()))) {
            throw new ApplicationException(ErrorCode.INVALID_DATA,
                    "the Avalanche rail can only be chosen before funding and never for a contract that has a Solana escrow");
        }
        c.setPaymentNetworkId(properties.getNetwork());
        contracts.save(c);
        return view(userId, false, c);
    }

    @Transactional(readOnly = true)
    public ContractView status(UUID userId, boolean admin, UUID contractId) {
        WorkContract c = contract(contractId);
        requireParticipantOrAdmin(c, userId, admin);
        return view(userId, admin, c);
    }

    private ContractView view(UUID userId, boolean admin, WorkContract c) {
        boolean enabled = properties.getNetwork().equals(c.getPaymentNetworkId());
        if (!enabled) {
            return new ContractView(c.getPaymentNetworkId(), false, null, null, null, List.of(), "This contract does not use the Avalanche rail.");
        }
        String escrowId = AvalancheEscrowIds.fromMilestone(milestone(c.getId()).getId());
        EnvironmentInfo environment = gateway().environmentInfo();
        EscrowSnapshot snapshot = gateway().escrowSnapshot(new EscrowReference(escrowId));
        return new ContractView(c.getPaymentNetworkId(), true, environment, escrowId, snapshot,
                chainTransactions.findByContractIdOrderByIdAsc(c.getId()),
                "Demonstration only: " + environment.environment() + " simulated escrow, testnet USDC with no real-world value.",
                mutualRefundState(c.getId()));
    }

    private Milestone milestone(UUID contractId) {
        return milestones.findByContractId(contractId).orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "milestone of " + contractId));
    }

    private String walletAddress(UUID userId) {
        com.marketplace.backend.entity.EvmWallet linked = evmWallets == null ? null : evmWallets.findByUserId(userId).orElse(null);
        if (linked != null) {
            return linked.getAddress().toLowerCase(Locale.ROOT);
        }
        Wallet wallet = wallets.findFirstByUserIdOrderByIdAsc(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_DATA, "a linked wallet is required"));
        String address = wallet.getPublicKey();
        if (address == null || !gateway().validateAddress(new BlockchainAddress(address))) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "the linked wallet is not a valid Avalanche address");
        }
        return address.toLowerCase(Locale.ROOT);
    }

    private static String sha(String value) {
        try {
            return "0x" + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private JobSubmission recordedSubmission(WorkContract c, UUID submissionId) {
        JobSubmission s = submissionId == null ? null : submissions.findById(submissionId).orElse(null);
        if (s == null || !c.getId().equals(s.getContractId())) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "a recorded submission of this contract is required");
        }
        return s;
    }

    private ContractDispute recordedDispute(WorkContract c, UUID disputeId) {
        ContractDispute d = disputeId == null ? null : disputes.findById(disputeId).orElse(null);
        if (d == null || !c.getId().equals(d.getContractId())) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "a recorded dispute of this contract is required");
        }
        return d;
    }

    /** Checks the caller's right to perform the action and builds the gateway parameters from recorded data only. */
    Map<String, Object> authorize(UUID userId, boolean admin, WorkContract c, String action, Input in) {
        if (!ACTIONS.contains(action)) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "unknown action " + action);
        }
        requireAvalanche(c);
        boolean client = isClient(c, userId);
        boolean freelancer = isFreelancer(c, userId);
        Map<String, Object> p = new HashMap<>();
        switch (action) {
            case "fund" -> {
                require(client, "only the client funds");
                require(c.getStatus() == ContractStatus.PENDING_FUNDING, "the contract is not awaiting funding");
                Milestone m = milestone(c.getId());
                require(c.getDeliveryDueAt() != null, "a delivery date is required");
                require(m.getAmount() != null && m.getAmount().compareTo(c.getBudgetUsd()) == 0, "the milestone amount differs from the contract budget");
                require(c.getReviewWindowHours() == com.marketplace.backend.entity.PaymentFlow.REVIEW_WINDOW_HOURS, "the review window differs from the recorded rule");
                p.put("client", walletAddress(c.getClientUserId()));
                p.put("freelancer", walletAddress(c.getFreelancerId()));
                p.put("amount", baseUnits(c.getBudgetUsd()));
                p.put("fundingExpiresAt", Instant.now().plus(FUNDING_WINDOW).getEpochSecond());
                p.put("deliveryDueAt", c.getDeliveryDueAt().getEpochSecond());
                p.put("reviewWindowHours", c.getReviewWindowHours());
                p.put("maxRevisions", c.getMaxRevisions());
                p.put("highValueGrace", false);
            }
            case "submit" -> {
                require(freelancer, "only the freelancer submits");
                JobSubmission s = recordedSubmission(c, in.submissionId());
                require(s.getStatus() == JobSubmissionStatus.SUBMITTED, "only a submission in review can be anchored");
                p.put("evidenceHash", sha("avalanche-evidence:" + c.getId() + ":" + s.getId() + ":" + s.getPayloadHash()));
            }
            case "revise" -> {
                require(client, "only the client requests a revision");
                JobSubmission s = recordedSubmission(c, in.submissionId());
                require(s.getStatus() == JobSubmissionStatus.REVISION_REQUESTED && s.getReviewerFeedback() != null && !s.getReviewerFeedback().isBlank(),
                        "a recorded revision request with feedback is required");
                p.put("feedbackHash", sha("avalanche-feedback:" + s.getId() + ":" + s.getReviewerFeedback()));
            }
            case "extension-request" -> {
                require(freelancer, "only the freelancer requests an extension");
                require(in.newDeliveryDueAt() != null, "the requested delivery date is required");
                p.put("newDeliveryDueAt", in.newDeliveryDueAt());
            }
            case "extension-approve" -> require(client, "only the client approves an extension");
            case "dispute" -> {
                require(client || freelancer, "only a party opens a dispute");
                ContractDispute d = recordedDispute(c, in.disputeId());
                require(userId.equals(d.getOpenedBy()), "the recorded dispute was opened by someone else");
                p.put("reasonHash", sha("avalanche-dispute:" + d.getId() + ":" + d.getReasonCode()));
                p.put("by", freelancer ? "freelancer" : "client");
            }
            case "release" -> {
                require(client || freelancer, "only a party releases");
                p.put("by", client ? "client" : "relayer");
            }
            case "resolve" -> {
                require(admin, "only the arbiter role (administrator) resolves a dispute");
                ContractDispute d = recordedDispute(c, in.disputeId());
                require(d.getStatus() == DisputeStatus.DECISION_PENDING_RELEASE || d.getStatus() == DisputeStatus.DECISION_PENDING_REFUND,
                        "no recorded administrator decision awaits settlement");
                boolean release = d.getStatus() == DisputeStatus.DECISION_PENDING_RELEASE;
                p.put("releaseToFreelancer", release);
                p.put("resolutionHash", sha("avalanche-resolution:" + d.getId() + ":" + (release ? "RELEASE" : "REFUND")));
            }
            case "mutual-refund" -> {
                require(client || freelancer, "only a party takes part in a mutual refund");
                if (in.clientSignature() != null) {
                    p.put("clientSignature", in.clientSignature());
                }
                if (in.freelancerSignature() != null) {
                    p.put("freelancerSignature", in.freelancerSignature());
                }
                if (in.authorizationDeadline() != null) {
                    p.put("authorizationDeadline", in.authorizationDeadline());
                }
                if (in.clientSignature() == null && in.freelancerSignature() == null && in.authorizationDeadline() == null) {
                    // F13: both parties signed through the marketplace; use the held pair and the deadline they cover
                    pendingAuthorizations.findByContractIdAndAction(c.getId(), MUTUAL_REFUND)
                            .filter(a -> a.getClientSignature() != null && a.getFreelancerSignature() != null)
                            .filter(a -> a.getAuthorizationDeadline() > Instant.now().getEpochSecond())
                            .ifPresent(a -> {
                                p.put("clientSignature", a.getClientSignature());
                                p.put("freelancerSignature", a.getFreelancerSignature());
                                p.put("authorizationDeadline", a.getAuthorizationDeadline());
                            });
                }
            }
            default -> throw new ApplicationException(ErrorCode.INVALID_DATA, "unknown action " + action);
        }
        return p;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, message);
        }
    }

    private static String baseUnits(BigDecimal usd) {
        return usd.movePointRight(6).toBigIntegerExact().toString();
    }

    @Transactional(readOnly = true)
    public PreparedAction prepare(UUID userId, boolean admin, UUID contractId, String action, Input in) {
        WorkContract c = contract(contractId);
        Map<String, Object> params = authorize(userId, admin, c, action, in == null ? Input.empty() : in);
        String escrowId = AvalancheEscrowIds.fromMilestone(milestone(contractId).getId());
        return gateway().prepareAction(new EscrowActionRequest(escrowId, action, null, params));
    }

    /** Executes with the gateway's LOCAL development wallets. The attempt is recorded BEFORE the gateway is called. */
    @Transactional
    public AvalancheChainTransaction execute(UUID userId, boolean admin, UUID contractId, String action, Input in) {
        Input input = in == null ? Input.empty() : in;
        WorkContract c = contract(contractId);
        Map<String, Object> params = authorize(userId, admin, c, action, input);
        Milestone m = milestone(contractId);
        String escrowId = AvalancheEscrowIds.fromMilestone(m.getId());
        String operationKey = contractId + ":" + action + ":" + (input.idempotencyKey() != null && !input.idempotencyKey().isBlank()
                ? input.idempotencyKey() : String.valueOf(chainTransactions.countByContractIdAndAction(contractId, action) + 1));
        AvalancheChainTransaction existing = chainTransactions.findByNetworkIdAndOperationKey(properties.getNetwork(), operationKey).orElse(null);
        if (existing != null) {
            return existing;
        }
        EnvironmentInfo environment = gateway().environmentInfo();
        AvalancheChainTransaction row = newRow(userId, contractId, m.getId(), escrowId, action, operationKey, environment);
        chainTransactions.saveAndFlush(row);
        try {
            TransactionResult result = gateway().executeAction(new EscrowActionRequest(escrowId, action, operationKey, params));
            apply(row, result);
        } catch (AvalancheGatewayException e) {
            row.setStatus(e.isDefinitive() ? TransactionStatus.FAILED.name() : TransactionStatus.UNKNOWN.name());
            row.setErrorClass(e.getCode() != null ? e.getCode() : (e.isDefinitive() ? "GATEWAY_REJECTED" : "GATEWAY_UNREACHABLE"));
        }
        row.setUpdatedAt(Instant.now());
        return chainTransactions.save(row);
    }

    /** Records a transaction the user's own wallet broadcast, after checking the user may perform that action. */
    @Transactional
    public AvalancheChainTransaction recordUserTransaction(UUID userId, boolean admin, UUID contractId, String action, String txHash, Input in) {
        WorkContract c = contract(contractId);
        authorize(userId, admin, c, action, in == null ? Input.empty() : in);
        if (txHash == null || !txHash.matches("0x[0-9a-fA-F]{64}")) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "a 32-byte transaction hash is required");
        }
        String key = contractId + ":" + action + ":" + txHash.toLowerCase(Locale.ROOT);
        AvalancheChainTransaction existing = chainTransactions.findByNetworkIdAndOperationKey(properties.getNetwork(), key).orElse(null);
        if (existing != null) {
            return existing;
        }
        Milestone m = milestone(contractId);
        EnvironmentInfo environment = gateway().environmentInfo();
        AvalancheChainTransaction row = newRow(userId, contractId, m.getId(), AvalancheEscrowIds.fromMilestone(m.getId()), action, key, environment);
        row.setTxHash(txHash.toLowerCase(Locale.ROOT));
        row.setStatus(TransactionStatus.SUBMITTED.name());
        return chainTransactions.save(row);
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

    /** Refreshes unfinished transactions from the gateway and applies business effects of FINALIZED ones, once. */
    @Transactional
    public ContractView refresh(UUID userId, boolean admin, UUID contractId) {
        WorkContract c = contract(contractId);
        requireParticipantOrAdmin(c, userId, admin);
        requireAvalanche(c);
        for (AvalancheChainTransaction row : chainTransactions.findByContractIdOrderByIdAsc(contractId)) {
            if (!TransactionStatus.valueOf(row.getStatus()).isTerminal()) {
                String reference = row.getTxHash() != null && row.getOperationKey().endsWith(row.getTxHash()) ? row.getTxHash() : row.getOperationKey();
                try {
                    apply(row, gateway().transactionStatus(new TransactionReference(gateway().network(), reference)));
                } catch (AvalancheGatewayException e) {
                    row.setErrorClass(e.getCode() != null ? e.getCode() : "GATEWAY_UNREACHABLE");
                }
                row.setUpdatedAt(Instant.now());
            }
            if (TransactionStatus.FINALIZED.name().equals(row.getStatus()) && !row.isBusinessApplied()) {
                applyBusinessEffect(c, row);
            }
            chainTransactions.save(row);
        }
        return view(userId, admin, c);
    }

    /**
     * F13: stores the caller's signature of the mutual-refund typed data. The deadline and digest are re-derived from the
     * gateway (which bounds the deadline by its TTL and binds escrow, nonce and chain), so a party can only sign what the
     * escrow currently expects. A different deadline or digest starts a new pair (the other party must sign again). Each
     * party can write only its own signature. The escrow contract verifies both signers on chain.
     */
    @Transactional
    public ContractView submitMutualRefundSignature(UUID userId, UUID contractId, Long authorizationDeadline, String typedDataDigest,
                                                    String signature) {
        WorkContract c = contract(contractId);
        requireAvalanche(c);
        boolean client = isClient(c, userId);
        boolean freelancer = isFreelancer(c, userId);
        require(client || freelancer, "only a party takes part in a mutual refund");
        require(authorizationDeadline != null && authorizationDeadline > Instant.now().getEpochSecond(), "the authorization has expired");
        require(typedDataDigest != null && DIGEST_HEX.matcher(typedDataDigest).matches(), "a 32-byte typed-data digest is required");
        require(signature != null && SIGNATURE_HEX.matcher(signature).matches(), "a 65-byte signature is required");
        String escrowId = AvalancheEscrowIds.fromMilestone(milestone(contractId).getId());
        Map<String, Object> params = new HashMap<>();
        params.put("authorizationDeadline", authorizationDeadline);
        PreparedAction expected = gateway().prepareAction(new EscrowActionRequest(escrowId, MUTUAL_REFUND, null, params));
        require(typedDataDigest.equalsIgnoreCase(expected.typedDataDigest()), "the signed data does not match the current escrow authorization");

        Instant now = Instant.now();
        com.marketplace.backend.entity.AvalanchePendingAuthorization pending = pendingAuthorizations
                .findWithLockByContractIdAndAction(contractId, MUTUAL_REFUND).orElse(null);
        if (pending == null) {
            pending = new com.marketplace.backend.entity.AvalanchePendingAuthorization();
            pending.setContractId(contractId);
            pending.setAction(MUTUAL_REFUND);
            pending.setCreatedAt(now);
        }
        if (pending.getTypedDataDigest() == null || pending.getAuthorizationDeadline() != authorizationDeadline
                || !pending.getTypedDataDigest().equalsIgnoreCase(typedDataDigest)) {
            pending.setAuthorizationDeadline(authorizationDeadline);
            pending.setTypedDataDigest(typedDataDigest.toLowerCase(Locale.ROOT));
            pending.setClientSignature(null);
            pending.setFreelancerSignature(null);
        }
        if (client) {
            pending.setClientSignature(signature.toLowerCase(Locale.ROOT));
        } else {
            pending.setFreelancerSignature(signature.toLowerCase(Locale.ROOT));
        }
        pending.setUpdatedAt(now);
        pendingAuthorizations.saveAndFlush(pending);
        return view(userId, false, c);
    }

    private MutualRefundState mutualRefundState(UUID contractId) {
        return pendingAuthorizations.findByContractIdAndAction(contractId, MUTUAL_REFUND)
                .filter(a -> a.getAuthorizationDeadline() > Instant.now().getEpochSecond())
                .map(a -> new MutualRefundState(a.getAuthorizationDeadline(), a.getTypedDataDigest(),
                        a.getClientSignature() != null, a.getFreelancerSignature() != null))
                .orElse(null);
    }

    /** Business state moves only after the chain state, read independently, agrees with the recorded terms. */
    private void applyBusinessEffect(WorkContract c, AvalancheChainTransaction row) {
        EscrowSnapshot chain = gateway().escrowSnapshot(new EscrowReference(row.getEscrowId()));
        Milestone m = milestone(c.getId());
        NeutralEscrowStatus status = chain == null ? NeutralEscrowStatus.NONE : chain.status();
        switch (row.getAction()) {
            case "fund" -> {
                boolean consistent = status == NeutralEscrowStatus.FUNDED && chain.amountBaseUnits().equals(baseUnits(c.getBudgetUsd()))
                        && chain.client().equalsIgnoreCase(walletAddress(c.getClientUserId()))
                        && chain.freelancer().equalsIgnoreCase(walletAddress(c.getFreelancerId()));
                if (!consistent) {
                    row.setErrorClass("CHAIN_MISMATCH");
                    return;
                }
                if (m.getStatus() == MilestoneStatus.PENDING_FUNDING) {
                    m.setStatus(MilestoneStatus.FUNDED);
                    c.setStatus(ContractStatus.ACTIVE);
                }
            }
            case "release", "resolve" -> {
                if (status == NeutralEscrowStatus.RELEASED) {
                    m.setStatus(MilestoneStatus.RELEASED);
                    c.setStatus(ContractStatus.COMPLETED);
                } else if (status == NeutralEscrowStatus.REFUNDED && row.getAction().equals("resolve")) {
                    m.setStatus(MilestoneStatus.REFUNDED);
                    c.setStatus(ContractStatus.CANCELLED);
                } else {
                    row.setErrorClass("CHAIN_MISMATCH");
                    return;
                }
            }
            case "mutual-refund" -> {
                if (status != NeutralEscrowStatus.REFUNDED) {
                    row.setErrorClass("CHAIN_MISMATCH");
                    return;
                }
                m.setStatus(MilestoneStatus.REFUNDED);
                c.setStatus(ContractStatus.CANCELLED);
                pendingAuthorizations.findByContractIdAndAction(c.getId(), MUTUAL_REFUND).ifPresent(pendingAuthorizations::delete);
            }
            default -> {
                row.setBusinessApplied(true);
                return;
            }
        }
        milestones.save(m);
        contracts.save(c);
        updateJob(c, m.getStatus());
        row.setBusinessApplied(true);
        row.setErrorClass(null);
        notifySettlement(c, row.getAction(), m.getStatus());
    }

    /** Mirrors SolanaEscrowFundingService / SolanaEscrowTimeoutService: the job status follows an applied escrow outcome. */
    private void updateJob(WorkContract c, MilestoneStatus result) {
        if (jobs == null || c.getJobId() == null) {
            return;
        }
        jobs.findById(c.getJobId()).ifPresent(job -> {
            com.marketplace.backend.entity.JobStatus before = job.getStatus();
            if (result == MilestoneStatus.FUNDED && before == com.marketplace.backend.entity.JobStatus.AWAITING_PAYMENT) {
                job.setStatus(com.marketplace.backend.entity.JobStatus.IN_PROGRESS);
            } else if (result == MilestoneStatus.RELEASED) {
                job.setStatus(com.marketplace.backend.entity.JobStatus.COMPLETED);
            } else if (result == MilestoneStatus.REFUNDED) {
                job.setStatus(com.marketplace.backend.entity.JobStatus.CANCELLED);
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
    private void notifySettlement(WorkContract c, String action, MilestoneStatus result) {
        if (!properties.isSettlementNotifications() || notifications == null) {
            return;
        }
        com.marketplace.backend.entity.NotificationType type;
        String title;
        if (action.equals("fund")) {
            type = com.marketplace.backend.entity.NotificationType.FUNDING_CONFIRMED;
            title = "Ký quỹ Avalanche (mô phỏng) đã được xác nhận";
        } else if (result == MilestoneStatus.RELEASED) {
            type = com.marketplace.backend.entity.NotificationType.RELEASE_CONFIRMED;
            title = "Ký quỹ Avalanche (mô phỏng) đã giải ngân cho Freelancer";
        } else if (result == MilestoneStatus.REFUNDED) {
            type = com.marketplace.backend.entity.NotificationType.REFUND_CONFIRMED;
            title = "Ký quỹ Avalanche (mô phỏng) đã hoàn cho Client";
        } else {
            return;
        }
        String message = "Giao dịch đã chốt trên chuỗi. Demonstration only: USDC mô phỏng hoặc testnet, không có tiền thật.";
        for (UUID party : List.of(c.getClientUserId(), c.getFreelancerId())) {
            notifications.notify(party, type, title, message, c.getJobId(), c.getBudgetUsd());
        }
    }
}
