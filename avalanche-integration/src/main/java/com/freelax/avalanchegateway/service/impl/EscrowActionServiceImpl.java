package com.freelax.avalanchegateway.service.impl;

import com.freelax.avalanchegateway.applicationException.ApiException;
import com.freelax.avalanchegateway.applicationException.AvalancheErrorDefinitions;
import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.contract.DeploymentHolder;
import com.freelax.avalanchegateway.contract.EscrowAuthorizations;
import com.freelax.avalanchegateway.contract.EscrowCalls;
import com.freelax.avalanchegateway.contract.EscrowReader;
import com.freelax.avalanchegateway.contract.EscrowState;
import com.freelax.avalanchegateway.contract.FundTerms;
import com.freelax.avalanchegateway.contract.VerifiedDeployment;
import com.freelax.avalanchegateway.dto.request.ActionRequest;
import com.freelax.avalanchegateway.dto.request.FaucetRequest;
import com.freelax.avalanchegateway.dto.request.SubmitRequest;
import com.freelax.avalanchegateway.dto.response.FaucetResponse;
import com.freelax.avalanchegateway.dto.response.PreparedActionResponse;
import com.freelax.avalanchegateway.dto.response.PreparedTx;
import com.freelax.avalanchegateway.dto.response.TransactionResponse;
import com.freelax.avalanchegateway.entity.TxRecord;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.exception.IdempotencyConflictException;
import com.freelax.avalanchegateway.mapper.TransactionResponseMapper;
import com.freelax.avalanchegateway.repository.TxRepository;
import com.freelax.avalanchegateway.service.EscrowActionService;
import com.freelax.avalanchegateway.service.TransactionSubmissionService;
import com.freelax.avalanchegateway.signer.Signature;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.freelax.avalanchegateway.service.impl.RequestFields.address;
import static com.freelax.avalanchegateway.service.impl.RequestFields.bytes32;
import static com.freelax.avalanchegateway.service.impl.RequestFields.escrowId;
import static com.freelax.avalanchegateway.service.impl.RequestFields.required;

/**
 * The authorization signature exists only where the AUTHORIZATION development signer exists, so in AVALANCHE_FUJI the actions
 * that need one are reported as unavailable instead of being signed by anything.
 */
@Service
@ConditionalOnProperty(name = "avalanche.tx.enabled", havingValue = "true")
public class EscrowActionServiceImpl implements EscrowActionService {

    private static final long AUTHORIZATION_TTL_SECONDS = 600;
    private static final BigInteger MAX_FAUCET = BigInteger.valueOf(1_000_000_000_000L);
    private static final int ESCROW_STATUS_DISPUTED = 4;
    private static final long GAS_APPROVE = 150_000;
    private static final long GAS_FUND = 1_000_000;
    private static final long GAS_ACTION = 500_000;
    private static final long GAS_FAUCET = 100_000;

    private final ChainClient client;
    private final ValidatedEnvironment environment;
    private final DeploymentHolder deployment;
    private final SignerRegistry registry;
    private final TransactionSubmissionService submission;
    private final TxRepository repository;
    private final TransactionResponseMapper mapper;

    public EscrowActionServiceImpl(ChainClient client, ValidatedEnvironment environment, DeploymentHolder deployment,
                                   ObjectProvider<SignerRegistry> registry, ObjectProvider<TransactionSubmissionService> submission,
                                   TxRepository repository, TransactionResponseMapper mapper) {
        this.client = client;
        this.environment = environment;
        this.deployment = deployment;
        this.registry = registry.getIfAvailable();
        this.submission = submission.getIfAvailable();
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PreparedActionResponse prepare(String rawEscrowId, String action, ActionRequest r) {
        String id = escrowId(rawEscrowId);
        deployment.require();
        return switch (action) {
            case "fund" -> prepareFund(id, r);
            case "submit" -> prepareSubmit(id, r);
            case "revise" -> prepareRevision(id, r);
            case "extension-request" -> prepareExtensionRequest(id, r);
            case "extension-approve" -> prepareSingleCall(id, "extension-approve", "approveExtension", EscrowCalls.approveExtension(id));
            case "dispute" -> prepareSingleCall(id, "dispute", "openDispute", EscrowCalls.openDispute(id, bytes32(r.reasonHash(), "reasonHash")));
            case "release" -> prepareSingleCall(id, "release", "release", EscrowCalls.release(id));
            case "resolve" -> prepareResolution(id, r);
            case "mutual-refund" -> prepareMutualRefund(id, r);
            case "mutual-settle" -> prepareMutualSettlement(id, r);
            default -> throw unknownAction(action);
        };
    }

    @Override
    public TransactionResponse execute(String rawEscrowId, String action, ActionRequest r) {
        return mapper.toResponse(executeAction(rawEscrowId, action, r));
    }

    @Override
    public FaucetResponse faucet(FaucetRequest request) {
        requireFaucetEnvironment();
        if (submission == null) {
            throw new ApiException(AvalancheErrorDefinitions.SIGNER_UNAVAILABLE, "no backend development wallet exists in this process");
        }
        BigInteger value = faucetAmount(request.amount());
        String owner = address(request.address());
        String operationKey = "faucet:" + Hash.sha3String(owner + System.nanoTime()).substring(2, 18);
        TxRecord record = submission.submit(new SubmitRequest(operationKey, SignerRole.RELAYER, deployment.require().manifest().token(),
                EscrowCalls.erc20Mint(owner, value), GAS_FAUCET));
        return new FaucetResponse(environment.environment().name(), true, request.address(), request.amount(), mapper.toResponse(record));
    }

    private PreparedActionResponse prepareFund(String id, ActionRequest r) {
        SignerRegistry signers = authorizationSigners();
        String token = deployment.require().manifest().token();
        FundTerms terms = new FundTerms(id, address(r.client()), address(r.freelancer()), token,
                new BigInteger(required(r.amount(), "amount")), required(r.fundingExpiresAt(), "fundingExpiresAt"),
                required(r.deliveryDueAt(), "deliveryDueAt"), required(r.reviewWindowHours(), "reviewWindowHours"),
                required(r.maxRevisions(), "maxRevisions"), Boolean.TRUE.equals(r.highValueGrace()), reader().defaultArbiter());
        long deadline = authorizationDeadline();
        String typedData = authorizations().fundTypedData(terms, 0, deadline);
        Signature signature = EscrowAuthorizations.sign(signers.get(SignerRole.AUTHORIZATION), typedData);
        String escrow = escrowAddress();
        return response("fund", id, List.of(
                transaction("approve", token, EscrowCalls.erc20Approve(escrow, terms.amount())),
                transaction("fund", escrow, EscrowCalls.fund(terms, deadline, signatureBytes(signature)))), deadline);
    }

    private PreparedActionResponse prepareSubmit(String id, ActionRequest r) {
        String evidence = bytes32(r.evidenceHash(), "evidenceHash");
        EscrowState state = existingEscrow(id);
        long deadline = authorizationDeadline();
        Signature signature = EscrowAuthorizations.sign(authorizationSigners().get(SignerRole.AUTHORIZATION),
                authorizations().submitTypedData(id, evidence, state.submissionCount(), state.authNonce(), deadline));
        return response("submit", id, List.of(transaction("submit", escrowAddress(),
                EscrowCalls.submit(id, evidence, deadline, signatureBytes(signature)))), deadline);
    }

    private PreparedActionResponse prepareRevision(String id, ActionRequest r) {
        String feedback = bytes32(r.feedbackHash(), "feedbackHash");
        EscrowState state = existingEscrow(id);
        long deadline = authorizationDeadline();
        Signature signature = EscrowAuthorizations.sign(authorizationSigners().get(SignerRole.AUTHORIZATION),
                authorizations().revisionTypedData(id, feedback, state.revisionsUsed(), state.authNonce(), deadline));
        return response("revise", id, List.of(transaction("requestRevision", escrowAddress(),
                EscrowCalls.requestRevision(id, feedback, deadline, signatureBytes(signature)))), deadline);
    }

    private PreparedActionResponse prepareExtensionRequest(String id, ActionRequest r) {
        return prepareSingleCall(id, "extension-request", "requestExtension",
                EscrowCalls.requestExtension(id, required(r.newDeliveryDueAt(), "newDeliveryDueAt")));
    }

    private PreparedActionResponse prepareResolution(String id, ActionRequest r) {
        return prepareSingleCall(id, "resolve", "resolveDispute", EscrowCalls.resolveDispute(id,
                required(r.releaseToFreelancer(), "releaseToFreelancer"), bytes32(r.resolutionHash(), "resolutionHash")));
    }

    private PreparedActionResponse prepareSingleCall(String id, String action, String purpose, String calldata) {
        return response(action, id, List.of(transaction(purpose, escrowAddress(), calldata)), null);
    }

    /**
     * F13: both parties must sign the SAME typed data. The first prepare chooses the deadline; every later call (the second
     * signer, and the call carrying both signatures) passes it back and must reuse it.
     */
    private PreparedActionResponse prepareMutualRefund(String id, ActionRequest r) {
        EscrowState state = existingEscrow(id);
        long deadline = mutualRefundDeadline(r);
        String typedData = authorizations().mutualRefundTypedData(id, state.authNonce(), deadline);
        if (!hasBothSignatures(r)) {
            return typedDataResponse("mutual-refund", id, List.of(), deadline, typedData);
        }
        String calldata = EscrowCalls.mutualRefund(id, deadline, Numeric.hexStringToByteArray(r.clientSignature()),
                Numeric.hexStringToByteArray(r.freelancerSignature()));
        return typedDataResponse("mutual-refund", id, List.of(transaction("mutualRefund", escrowAddress(), calldata)), deadline, typedData);
    }

    /**
     * R12 (owner decision 2026-11-05): both parties settle a Disputed escrow. Same two-signature flow as F13; the first prepare
     * chooses the deadline, later calls pass it back. {@code resolutionHash} carries the settlement record hash.
     */
    private PreparedActionResponse prepareMutualSettlement(String id, ActionRequest r) {
        boolean toFreelancer = required(r.releaseToFreelancer(), "releaseToFreelancer");
        String settlement = bytes32(r.resolutionHash(), "resolutionHash");
        EscrowState state = existingEscrow(id);
        if (state.status() != ESCROW_STATUS_DISPUTED) {
            throw new ApiException(AvalancheErrorDefinitions.ESCROW_NOT_DISPUTED,
                    "a mutual settlement needs a Disputed escrow; it is " + state.statusName());
        }
        long deadline = mutualRefundDeadline(r);
        String typedData = authorizations().mutualSettlementTypedData(id, toFreelancer, state.amount(), settlement, state.disputedAt(),
                state.authNonce(), deadline);
        if (!hasBothSignatures(r)) {
            return typedDataResponse("mutual-settle", id, List.of(), deadline, typedData);
        }
        String calldata = EscrowCalls.mutualSettleDispute(id, toFreelancer, settlement, deadline,
                Numeric.hexStringToByteArray(r.clientSignature()), Numeric.hexStringToByteArray(r.freelancerSignature()));
        return typedDataResponse("mutual-settle", id, List.of(transaction("mutualSettleDispute", escrowAddress(), calldata)), deadline, typedData);
    }

    /**
     * Deadline of a mutual authorization (register F13). Without a supplied deadline a fresh one is chosen (first prepare, or the
     * LOCAL execute path). A supplied deadline must still be in the future and no further than the TTL from the latest block,
     * so a caller cannot create a long-lived authorization. Calldata with both signatures requires it.
     */
    long mutualRefundDeadline(ActionRequest r) {
        Long supplied = r.authorizationDeadline();

        if (supplied == null) {
            return defaultDeadline(r);
        }

        validateWithinWindow(supplied);
        return supplied;
    }

    private long defaultDeadline(ActionRequest r) {
        if (hasBothSignatures(r)) {
            throw new ApiException(AvalancheErrorDefinitions.DEADLINE_REQUIRED);
        }
        return authorizationDeadline();
    }

    private void validateWithinWindow(long deadline) {
        long latest = client.latestBlockTimestamp();
        boolean expired = deadline <= latest;
        boolean tooFar = deadline > latest + AUTHORIZATION_TTL_SECONDS;

        if (expired || tooFar) {
            throw new ApiException(AvalancheErrorDefinitions.DEADLINE_OUT_OF_RANGE);
        }
    }

    private TxRecord executeAction(String rawEscrowId, String action, ActionRequest r) {
        if (submission == null || registry == null) {
            throw new ApiException(AvalancheErrorDefinitions.SIGNER_UNAVAILABLE,
                    "no backend development wallet exists in " + environment.environment() + "; sign with the user's own wallet");
        }
        String id = escrowId(rawEscrowId);
        String operationKey = required(r.operationKey(), "operationKey");
        VerifiedDeployment verified = deployment.require();
        String escrow = verified.manifest().escrow();

        // Idempotent by operation key: a repeated request returns the existing transaction. The calldata is NOT regenerated,
        // because a fresh authorization (new deadline, new signature) would change the signed bytes for the same operation.
        Optional<TxRecord> existing = findTransaction(operationKey);
        if (existing.isPresent()) {
            return replay(existing.get(), escrow, operationKey);
        }

        if (action.equals("mutual-refund") && !hasBothSignatures(r)) {
            return submitMutualRefundSignedByBothParties(id, operationKey, escrow);
        }
        if (action.equals("mutual-settle") && !hasBothSignatures(r)) {
            return submitMutualSettlementSignedByBothParties(id, action, r, operationKey, escrow);
        }
        return submitPrepared(id, action, r, operationKey, verified);
    }

    private TxRecord replay(TxRecord existing, String escrow, String operationKey) {
        if (!existing.toAddress().equalsIgnoreCase(escrow)) {
            throw new IdempotencyConflictException(operationKey);
        }
        return existing;
    }

    private TxRecord submitMutualRefundSignedByBothParties(String id, String operationKey, String escrow) {
        EscrowState state = existingEscrow(id);
        long deadline = authorizationDeadline();
        String typedData = authorizations().mutualRefundTypedData(id, state.authNonce(), deadline);
        String calldata = EscrowCalls.mutualRefund(id, deadline, partySignature(SignerRole.TEST_BUYER, typedData),
                partySignature(SignerRole.TEST_FREELANCER, typedData));
        return submission.submit(new SubmitRequest(operationKey, SignerRole.RELAYER, escrow, calldata, GAS_ACTION));
    }

    private TxRecord submitMutualSettlementSignedByBothParties(String id, String action, ActionRequest r, String operationKey, String escrow) {
        PreparedActionResponse unsigned = prepare(id, action, r);
        String calldata = EscrowCalls.mutualSettleDispute(id, r.releaseToFreelancer(), r.resolutionHash().toLowerCase(Locale.ROOT),
                unsigned.authorizationDeadline(), partySignature(SignerRole.TEST_BUYER, unsigned.typedData()),
                partySignature(SignerRole.TEST_FREELANCER, unsigned.typedData()));
        return submission.submit(new SubmitRequest(operationKey, SignerRole.RELAYER, escrow, calldata, GAS_ACTION));
    }

    private TxRecord submitPrepared(String id, String action, ActionRequest r, String operationKey, VerifiedDeployment verified) {
        PreparedActionResponse prepared = prepare(id, action, r);
        SignerRole role = roleFor(action, r.by());
        if (action.equals("fund")) {
            submission.submit(new SubmitRequest(operationKey + ":approve", role, verified.manifest().token(),
                    prepared.transactions().get(0).data(), GAS_APPROVE));
            return submission.submit(new SubmitRequest(operationKey, role, verified.manifest().escrow(),
                    prepared.transactions().get(1).data(), GAS_FUND));
        }
        PreparedTx call = prepared.transactions().get(0);
        return submission.submit(new SubmitRequest(operationKey, role, call.to(), call.data(), GAS_ACTION));
    }

    private static SignerRole roleFor(String action, String by) {
        boolean freelancer = "freelancer".equalsIgnoreCase(by);
        boolean client = "client".equalsIgnoreCase(by);
        return switch (action) {
            case "fund", "extension-approve", "revise" -> SignerRole.TEST_BUYER;
            case "submit", "extension-request" -> SignerRole.TEST_FREELANCER;
            case "dispute" -> freelancer ? SignerRole.TEST_FREELANCER : SignerRole.TEST_BUYER;
            case "release" -> client ? SignerRole.TEST_BUYER : SignerRole.RELAYER;
            case "resolve" -> SignerRole.ARBITER;
            case "mutual-refund", "mutual-settle" -> SignerRole.RELAYER;
            default -> throw unknownAction(action);
        };
    }

    private void requireFaucetEnvironment() {
        boolean local = environment.environment() == AvalancheEnvironment.LOCAL_SIMULATION;
        if (!local || !"MOCK".equals(deployment.require().manifest().tokenKind())) {
            throw new ApiException(AvalancheErrorDefinitions.FAUCET_UNAVAILABLE);
        }
    }

    private static BigInteger faucetAmount(String amount) {
        BigInteger value;
        try {
            value = new BigInteger(required(amount, "amount"));
        } catch (NumberFormatException e) {
            throw new ApiException(AvalancheErrorDefinitions.INVALID_REQUEST, "amount must be a whole number of base units");
        }
        if (value.signum() <= 0 || value.compareTo(MAX_FAUCET) > 0) {
            throw new ApiException(AvalancheErrorDefinitions.INVALID_REQUEST, "amount must be between 1 and 1,000,000 USDC in base units");
        }
        return value;
    }

    private Optional<TxRecord> findTransaction(String operationKey) {
        return repository.find(environment.environment().name(), environment.chainId(), operationKey);
    }

    private EscrowState existingEscrow(String id) {
        EscrowState state = reader().read(id);
        if (state.status() == 0) {
            throw new ApiException(AvalancheErrorDefinitions.ESCROW_NOT_FOUND);
        }
        return state;
    }

    private EscrowReader reader() {
        return new EscrowReader(client, escrowAddress());
    }

    private String escrowAddress() {
        return deployment.require().manifest().escrow();
    }

    private EscrowAuthorizations authorizations() {
        return new EscrowAuthorizations(environment.chainId(), escrowAddress());
    }

    private SignerRegistry authorizationSigners() {
        if (registry == null) {
            throw new ApiException(AvalancheErrorDefinitions.AUTHORIZATION_UNAVAILABLE,
                    "this action needs a backend authorization signature and no backend signer exists in " + environment.environment());
        }
        return registry;
    }

    private long authorizationDeadline() {
        return client.latestBlockTimestamp() + AUTHORIZATION_TTL_SECONDS;
    }

    private static boolean hasBothSignatures(ActionRequest r) {
        return r.clientSignature() != null && r.freelancerSignature() != null;
    }

    private byte[] partySignature(SignerRole party, String typedData) {
        return signatureBytes(EscrowAuthorizations.sign(registry.get(party), typedData));
    }

    private static byte[] signatureBytes(Signature signature) {
        return Numeric.hexStringToByteArray(signature.toHex());
    }

    private static ApiException unknownAction(String action) {
        return new ApiException(AvalancheErrorDefinitions.UNKNOWN_ACTION, "unknown action " + action);
    }

    private PreparedActionResponse response(String action, String escrowId, List<PreparedTx> transactions, Long deadline) {
        return typedDataResponse(action, escrowId, transactions, deadline, null);
    }

    private PreparedActionResponse typedDataResponse(String action, String escrowId, List<PreparedTx> transactions, Long deadline,
                                                     String typedData) {
        String digest = typedData == null ? null : Numeric.toHexString(EscrowAuthorizations.digest(typedData));
        return new PreparedActionResponse(environment.environment().name(), true, environment.chainId(), action, escrowId, transactions,
                deadline, typedData, digest);
    }

    private static PreparedTx transaction(String purpose, String to, String data) {
        return new PreparedTx(purpose, to, data, "0");
    }
}
