package com.freelax.avalanchegateway.api;

import com.freelax.avalanchegateway.api.Dtos.ActionRequest;
import com.freelax.avalanchegateway.api.Dtos.PreparedActionResponse;
import com.freelax.avalanchegateway.api.Dtos.PreparedTx;
import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.TxReceipt;
import com.freelax.avalanchegateway.config.TxProperties;
import com.freelax.avalanchegateway.contract.DeploymentHolder;
import com.freelax.avalanchegateway.contract.EscrowAuthorizations;
import com.freelax.avalanchegateway.contract.EscrowCalls;
import com.freelax.avalanchegateway.contract.EscrowReader;
import com.freelax.avalanchegateway.contract.EscrowState;
import com.freelax.avalanchegateway.contract.FundTerms;
import com.freelax.avalanchegateway.contract.VerifiedDeployment;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.signer.Signature;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import com.freelax.avalanchegateway.tx.SubmissionService;
import com.freelax.avalanchegateway.tx.SubmissionService.SubmitRequest;
import com.freelax.avalanchegateway.tx.TxRecord;
import com.freelax.avalanchegateway.tx.TxRepository;
import com.freelax.avalanchegateway.tx.TxState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Builds the calldata and the authorizations for every escrow action and, in LOCAL_SIMULATION only, executes them with the
 * development wallets (owner decision O-11). Which signer role performs an action is decided here from the action, never by the
 * caller. The authorization signature exists only where the AUTHORIZATION development signer exists, so in AVALANCHE_FUJI the
 * actions that need one are reported as unavailable instead of being signed by anything.
 */
@Service
@ConditionalOnProperty(name = "avalanche.tx.enabled", havingValue = "true")
public class EscrowActionService {

    private static final Pattern BYTES32 = Pattern.compile("0x[0-9a-fA-F]{64}");
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final long AUTHORIZATION_TTL_SECONDS = 600;
    private static final BigInteger MAX_FAUCET = BigInteger.valueOf(1_000_000_000_000L);

    private final ChainClient client;
    private final ValidatedEnvironment environment;
    private final DeploymentHolder deployment;
    private final SignerRegistry registry;
    private final SubmissionService submission;
    private final TxRepository repository;
    private final TxProperties txProperties;

    public EscrowActionService(ChainClient client, ValidatedEnvironment environment, DeploymentHolder deployment,
                               ObjectProvider<SignerRegistry> registry, ObjectProvider<SubmissionService> submission, TxRepository repository,
                               TxProperties txProperties) {
        this.client = client;
        this.environment = environment;
        this.deployment = deployment;
        this.registry = registry.getIfAvailable();
        this.submission = submission.getIfAvailable();
        this.repository = repository;
        this.txProperties = txProperties;
    }

    public VerifiedDeployment deployment() {
        if (deployment.deployment() == null) {
            throw new ApiException(503, "DEPLOYMENT_NOT_CONFIGURED", "no verified deployment manifest is configured");
        }
        return deployment.deployment();
    }

    public ValidatedEnvironment environment() {
        return environment;
    }

    public boolean backendSignersAvailable() {
        return registry != null;
    }

    public EscrowReader reader() {
        return new EscrowReader(client, deployment().manifest().escrow());
    }

    public ChainClient chain() {
        return client;
    }

    public static String requireAddress(String value) {
        if (value == null || !ADDRESS.matcher(value).matches()) {
            throw new ApiException(400, "INVALID_ADDRESS", "a 20-byte hex address is required");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    public static String requireEscrowId(String value) {
        if (value == null || !BYTES32.matcher(value).matches()) {
            throw new ApiException(400, "INVALID_ESCROW_ID", "the escrow id must be a 32-byte hex value");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String hash(String value, String field) {
        if (value == null || !BYTES32.matcher(value).matches()) {
            throw new ApiException(400, "INVALID_REQUEST", field + " must be a 32-byte hex value");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new ApiException(400, "INVALID_REQUEST", field + " is required");
        }
        return value;
    }

    private EscrowAuthorizations authorizations() {
        return new EscrowAuthorizations(environment.chainId(), deployment().manifest().escrow());
    }

    private SignerRegistry authorizationSigners() {
        if (registry == null) {
            throw new ApiException(409, "AUTHORIZATION_UNAVAILABLE",
                    "this action needs a backend authorization signature and no backend signer exists in " + environment.environment());
        }
        return registry;
    }

    private long authorizationDeadline() {
        return client.latestBlockTimestamp() + AUTHORIZATION_TTL_SECONDS;
    }

    /**
     * Deadline of a mutual refund authorization (register F13). Without a supplied deadline a fresh one is chosen (first
     * prepare, or the LOCAL execute path). A supplied deadline must still be in the future and no further than the TTL from
     * the latest block, so a caller cannot create a long-lived authorization. Calldata with both signatures requires it.
     */
    long mutualRefundDeadline(ActionRequest r) {
        if (r.authorizationDeadline() == null) {
            if (r.clientSignature() != null && r.freelancerSignature() != null) {
                throw new ApiException(400, "DEADLINE_REQUIRED", "both signatures were given without the deadline they cover");
            }
            return authorizationDeadline();
        }
        long latest = client.latestBlockTimestamp();
        long supplied = r.authorizationDeadline();
        if (supplied <= latest || supplied > latest + AUTHORIZATION_TTL_SECONDS) {
            throw new ApiException(400, "DEADLINE_OUT_OF_RANGE", "the authorization deadline is expired or beyond the allowed window");
        }
        return supplied;
    }

    private PreparedActionResponse response(String action, String escrowId, List<PreparedTx> txs, Long deadline, String typedData, String digest) {
        return new PreparedActionResponse(environment.environment().name(), true, environment.chainId(), action, escrowId, txs, deadline, typedData, digest);
    }

    private PreparedTx tx(String purpose, String to, String data) {
        return new PreparedTx(purpose, to, data, "0");
    }

    /** Calldata (and, where the contract needs one, the backend authorization) for an action. Nothing is sent. */
    public PreparedActionResponse prepare(String rawEscrowId, String action, ActionRequest r) {
        String id = requireEscrowId(rawEscrowId);
        String escrow = deployment().manifest().escrow();
        switch (action) {
            case "fund" -> {
                SignerRegistry signers = authorizationSigners();
                FundTerms terms = new FundTerms(id, requireAddress(r.client()), requireAddress(r.freelancer()), deployment().manifest().token(),
                        new BigInteger(required(r.amount(), "amount")), required(r.fundingExpiresAt(), "fundingExpiresAt"),
                        required(r.deliveryDueAt(), "deliveryDueAt"), required(r.reviewWindowHours(), "reviewWindowHours"),
                        required(r.maxRevisions(), "maxRevisions"), Boolean.TRUE.equals(r.highValueGrace()), reader().defaultArbiter());
                long deadline = authorizationDeadline();
                String typed = authorizations().fundTypedData(terms, 0, deadline);
                Signature sig = EscrowAuthorizations.sign(signers.get(SignerRole.AUTHORIZATION), typed);
                return response(action, id, List.of(
                        tx("approve", deployment().manifest().token(), EscrowCalls.erc20Approve(escrow, terms.amount())),
                        tx("fund", escrow, EscrowCalls.fund(terms, deadline, Numeric.hexStringToByteArray(sig.toHex())))),
                        deadline, null, null);
            }
            case "submit" -> {
                String evidence = hash(r.evidenceHash(), "evidenceHash");
                EscrowState s = reader().read(id);
                requireExists(s);
                long deadline = authorizationDeadline();
                Signature sig = EscrowAuthorizations.sign(authorizationSigners().get(SignerRole.AUTHORIZATION),
                        authorizations().submitTypedData(id, evidence, s.submissionCount(), s.authNonce(), deadline));
                return response(action, id, List.of(tx("submit", escrow, EscrowCalls.submit(id, evidence, deadline, Numeric.hexStringToByteArray(sig.toHex())))),
                        deadline, null, null);
            }
            case "revise" -> {
                String feedback = hash(r.feedbackHash(), "feedbackHash");
                EscrowState s = reader().read(id);
                requireExists(s);
                long deadline = authorizationDeadline();
                Signature sig = EscrowAuthorizations.sign(authorizationSigners().get(SignerRole.AUTHORIZATION),
                        authorizations().revisionTypedData(id, feedback, s.revisionsUsed(), s.authNonce(), deadline));
                return response(action, id, List.of(tx("requestRevision", escrow, EscrowCalls.requestRevision(id, feedback, deadline, Numeric.hexStringToByteArray(sig.toHex())))),
                        deadline, null, null);
            }
            case "extension-request" -> {
                return response(action, id, List.of(tx("requestExtension", escrow, EscrowCalls.requestExtension(id, required(r.newDeliveryDueAt(), "newDeliveryDueAt")))), null, null, null);
            }
            case "extension-approve" -> {
                return response(action, id, List.of(tx("approveExtension", escrow, EscrowCalls.approveExtension(id))), null, null, null);
            }
            case "dispute" -> {
                return response(action, id, List.of(tx("openDispute", escrow, EscrowCalls.openDispute(id, hash(r.reasonHash(), "reasonHash")))), null, null, null);
            }
            case "release" -> {
                return response(action, id, List.of(tx("release", escrow, EscrowCalls.release(id))), null, null, null);
            }
            case "resolve" -> {
                return response(action, id, List.of(tx("resolveDispute", escrow, EscrowCalls.resolveDispute(id,
                        required(r.releaseToFreelancer(), "releaseToFreelancer"), hash(r.resolutionHash(), "resolutionHash")))), null, null, null);
            }
            case "mutual-refund" -> {
                EscrowState s = reader().read(id);
                requireExists(s);
                // F13: both parties must sign the SAME typed data. The first prepare chooses the deadline; every later call
                // (the second signer, and the call carrying both signatures) passes it back and must reuse it.
                long deadline = mutualRefundDeadline(r);
                String typed = authorizations().mutualRefundTypedData(id, s.authNonce(), deadline);
                String digest = Numeric.toHexString(EscrowAuthorizations.digest(typed));
                if (r.clientSignature() == null || r.freelancerSignature() == null) {
                    return response(action, id, List.of(), deadline, typed, digest);
                }
                return response(action, id, List.of(tx("mutualRefund", escrow, EscrowCalls.mutualRefund(id, deadline,
                        Numeric.hexStringToByteArray(r.clientSignature()), Numeric.hexStringToByteArray(r.freelancerSignature())))), deadline, typed, digest);
            }
            default -> throw new ApiException(400, "UNKNOWN_ACTION", "unknown action " + action);
        }
    }

    private static void requireExists(EscrowState s) {
        if (s.status() == 0) {
            throw new ApiException(404, "ESCROW_NOT_FOUND", "no escrow with this id on this chain");
        }
    }

    /** Executes an action with the development wallets. Exists only where backend signers exist (LOCAL_SIMULATION). */
    public TxRecord execute(String rawEscrowId, String action, ActionRequest r) {
        if (submission == null || registry == null) {
            throw new ApiException(409, "SIGNER_UNAVAILABLE",
                    "no backend development wallet exists in " + environment.environment() + "; sign with the user's own wallet");
        }
        String id = requireEscrowId(rawEscrowId);
        String operationKey = required(r.operationKey(), "operationKey");
        String token = deployment().manifest().token();
        String escrow = deployment().manifest().escrow();

        // Idempotent by operation key: a repeated request returns the existing transaction. The calldata is NOT regenerated,
        // because a fresh authorization (new deadline, new signature) would change the signed bytes for the same operation.
        Optional<TxRecord> existing = transaction(operationKey);
        if (existing.isPresent()) {
            if (!existing.get().toAddress().equalsIgnoreCase(escrow)) {
                throw new SubmissionService.IdempotencyConflictException(operationKey);
            }
            return existing.get();
        }

        if (action.equals("mutual-refund") && (r.clientSignature() == null || r.freelancerSignature() == null)) {
            EscrowState s = reader().read(id);
            requireExists(s);
            long deadline = authorizationDeadline();
            String typed = authorizations().mutualRefundTypedData(id, s.authNonce(), deadline);
            Signature buyer = EscrowAuthorizations.sign(registry.get(SignerRole.TEST_BUYER), typed);
            Signature freelancer = EscrowAuthorizations.sign(registry.get(SignerRole.TEST_FREELANCER), typed);
            String data = EscrowCalls.mutualRefund(id, deadline, Numeric.hexStringToByteArray(buyer.toHex()), Numeric.hexStringToByteArray(freelancer.toHex()));
            return submission.submit(new SubmitRequest(operationKey, SignerRole.RELAYER, escrow, data, 500_000));
        }
        PreparedActionResponse prepared = prepare(id, action, r);
        SignerRole role = roleFor(action, r.by());
        if (action.equals("fund")) {
            submission.submit(new SubmitRequest(operationKey + ":approve", role, token, prepared.transactions().get(0).data(), 150_000));
            return submission.submit(new SubmitRequest(operationKey, role, escrow, prepared.transactions().get(1).data(), 1_000_000));
        }
        PreparedTx call = prepared.transactions().get(0);
        return submission.submit(new SubmitRequest(operationKey, role, call.to(), call.data(), 500_000));
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
            case "mutual-refund" -> SignerRole.RELAYER;
            default -> throw new ApiException(400, "UNKNOWN_ACTION", "unknown action " + action);
        };
    }

    /** Simulation Balance: mints the simulated USDC. Only for the mock token in LOCAL_SIMULATION. */
    public TxRecord faucet(String address, String amount) {
        if (environment.environment() != AvalancheEnvironment.LOCAL_SIMULATION || !"MOCK".equals(deployment().manifest().tokenKind())) {
            throw new ApiException(409, "FAUCET_UNAVAILABLE", "the simulated USDC faucet exists only in LOCAL_SIMULATION");
        }
        if (submission == null) {
            throw new ApiException(409, "SIGNER_UNAVAILABLE", "no backend development wallet exists in this process");
        }
        BigInteger value;
        try {
            value = new BigInteger(required(amount, "amount"));
        } catch (NumberFormatException e) {
            throw new ApiException(400, "INVALID_REQUEST", "amount must be a whole number of base units");
        }
        if (value.signum() <= 0 || value.compareTo(MAX_FAUCET) > 0) {
            throw new ApiException(400, "INVALID_REQUEST", "amount must be between 1 and 1,000,000 USDC in base units");
        }
        String op = "faucet:" + Hash.sha3String(requireAddress(address) + System.nanoTime()).substring(2, 18);
        return submission.submit(new SubmitRequest(op, SignerRole.RELAYER, deployment().manifest().token(),
                EscrowCalls.erc20Mint(requireAddress(address), value), 100_000));
    }

    public Optional<TxRecord> transaction(String operationKey) {
        return repository.find(environment.environment().name(), environment.chainId(), operationKey);
    }

    /**
     * Read-only status of a transaction broadcast by a user's own wallet: derived live from the chain, nothing is stored.
     * As for gateway-tracked transactions (plan 06 section 4), a receipt counts only when its target is one of the verified
     * manifest contracts; a receipt for any other target is reported UNKNOWN (it is not a transaction of this deployment).
     */
    public Dtos.HashStatusResponse statusByHash(String txHash) {
        if (txHash == null || !BYTES32.matcher(txHash).matches()) {
            throw new ApiException(400, "INVALID_TX_HASH", "a 32-byte transaction hash is required");
        }
        Optional<TxReceipt> receipt = client.receipt(txHash);
        String state;
        Long block = null;
        if (receipt.isPresent() && !isManifestTarget(receipt.get().to())) {
            state = TxState.UNKNOWN.name();
        } else if (receipt.isPresent()) {
            block = receipt.get().blockNumber();
            if (!receipt.get().success()) {
                state = TxState.REVERTED.name();
            } else {
                boolean finalized = environment.environment() == AvalancheEnvironment.LOCAL_SIMULATION
                        ? client.latestBlockNumber() >= block + txProperties.confirmations()
                        : client.finalizedBlockNumber() >= block;
                state = (finalized ? TxState.FINALIZED : TxState.CONFIRMED).name();
            }
        } else {
            state = client.transactionExists(txHash) ? TxState.PENDING.name() : TxState.UNKNOWN.name();
        }
        return new Dtos.HashStatusResponse(environment.environment().name(), true, environment.chainId(), txHash.toLowerCase(Locale.ROOT), state, block);
    }

    private boolean isManifestTarget(String to) {
        return to != null && deployment().allowedTargets().stream().anyMatch(to::equalsIgnoreCase);
    }
}
