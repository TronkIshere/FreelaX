package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.ChainClientException;
import com.freelax.avalanchegateway.chain.SendOutcome;
import com.freelax.avalanchegateway.config.TxProperties;
import com.freelax.avalanchegateway.env.EnvironmentGuard;
import com.freelax.avalanchegateway.signer.Signer;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.math.BigInteger;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Submits backend-signed transactions safely: one transaction per business operation, the raw transaction and hash are
 * persisted before anything is sent, a timeout is never a failure and never a second send, and nothing is ever re-signed
 * with a new nonce. Exists only where development signers exist (LOCAL_SIMULATION); there is no signing path for Fuji.
 * What to submit is decided by the caller (business authorization is not this class's job); value is always zero.
 */
public class SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9:_.\\-]{1,190}");
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final Pattern HEX_DATA = Pattern.compile("0x([0-9a-fA-F]{2})*");
    private static final int MAX_DATA_HEX_CHARS = 262_144;
    private static final BigInteger GWEI = BigInteger.valueOf(1_000_000_000L);

    public record SubmitRequest(String operationKey, SignerRole role, String to, String data, long gasLimit) {
    }

    public static class InvalidRequestException extends IllegalArgumentException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }

    public static class IdempotencyConflictException extends IllegalStateException {
        public IdempotencyConflictException(String operationKey) {
            super("IDEMPOTENCY_CONFLICT: operation " + operationKey + " already exists with different parameters");
        }
    }

    public static class SignerUnavailableException extends IllegalStateException {
        public SignerUnavailableException(String message) {
            super("SIGNER_UNAVAILABLE: " + message);
        }
    }

    public static class FeeCapExceededException extends IllegalStateException {
        public FeeCapExceededException() {
            super("FEE_CAP_EXCEEDED: the replacement fee would exceed the configured cap");
        }
    }

    private final ChainClient client;
    private final ValidatedEnvironment environment;
    private final SignerRegistry registry;
    private final TxRepository repository;
    private final NonceAllocator allocator;
    private final TransactionTemplate transactions;
    private final TxProperties properties;
    private final ErrorClassifier classifier;
    private final TxTransitions transitions;
    private final Clock clock;
    private final java.util.Set<String> allowedTargets;

    public SubmissionService(ChainClient client, ValidatedEnvironment environment, SignerRegistry registry,
                             TxRepository repository, NonceAllocator allocator, TransactionTemplate transactions,
                             TxProperties properties, ErrorClassifier classifier, TxTransitions transitions, Clock clock,
                             java.util.Set<String> allowedTargets) {
        EnvironmentGuard.requireSignerEnvironment(environment.environment());
        EnvironmentGuard.checkChainId(environment.environment(), environment.chainId());
        this.client = client;
        this.environment = environment;
        this.registry = registry;
        this.repository = repository;
        this.allocator = allocator;
        this.transactions = transactions;
        this.properties = properties;
        this.classifier = classifier;
        this.transitions = transitions;
        this.clock = clock;
        this.allowedTargets = allowedTargets;
    }

    /** Idempotent by operation key: a repeated call returns the existing record and never allocates a new nonce. */
    public TxRecord submit(SubmitRequest request) {
        validate(request);
        Signer signer = signerFor(request.role());
        String env = environment.environment().name();
        long chainId = environment.chainId();

        Optional<TxRecord> existing = repository.find(env, chainId, request.operationKey());
        if (existing.isPresent()) {
            return sameOrConflict(existing.get(), request);
        }
        long chainFloor = client.transactionCount(signer.address(), DefaultBlockParameterName.PENDING);
        BigInteger[] fees = suggestFees();
        TxRecord created;
        try {
            created = allocateAndInsert(request, signer, chainFloor, fees);
        } catch (DuplicateKeyException race) {
            TxRecord winner = repository.find(env, chainId, request.operationKey()).orElseThrow(() -> race);
            return sameOrConflict(winner, request);
        }
        return signAndSend(created, signer);
    }

    /**
     * Nonce allocation and the CREATED record are one database transaction: the nonce is consumed if and only if the record
     * exists. A deadlock or lock timeout rolls the whole transaction back, so retrying cannot burn a nonce.
     */
    private TxRecord allocateAndInsert(SubmitRequest request, Signer signer, long chainFloor, BigInteger[] fees) {
        String env = environment.environment().name();
        long chainId = environment.chainId();
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.execute(status -> {
                    long nonce = allocator.allocate(env, chainId, signer.address(), chainFloor, clock.millis());
                    long id = repository.insertCreated(request.operationKey(), env, chainId, request.role().name(), signer.address(),
                            request.to().toLowerCase(Locale.ROOT), request.data().toLowerCase(Locale.ROOT), request.gasLimit(),
                            fees[0], fees[1], nonce, clock.millis());
                    return repository.findById(id).orElseThrow();
                });
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= 5) {
                    throw e;
                }
                log.warn("nonce allocation lock conflict, retrying (attempt {})", attempt);
            }
        }
    }

    /**
     * Replaces the current attempt with a higher-fee transaction for the same nonce. The earlier hashes are kept: any of
     * them may still be the one that mines. The bump percentage is a conservative default, to be calibrated on Fuji.
     */
    public TxRecord speedUp(String operationKey) {
        TxRecord record = repository.find(environment.environment().name(), environment.chainId(), operationKey)
                .orElseThrow(() -> new InvalidRequestException("unknown operation " + operationKey));
        if (record.state().terminal() || record.state() == TxState.CREATED || record.state() == TxState.CONFIRMED) {
            throw new IllegalStateException("a " + record.state() + " operation cannot be sped up");
        }
        Signer signer = signerFor(SignerRole.valueOf(record.signerRole()));
        BigInteger cap = GWEI.multiply(BigInteger.valueOf(properties.maxFeeCapGwei()));
        BigInteger newMaxFee = bump(record.maxFeePerGas());
        BigInteger newPriority = bump(record.maxPriorityFeePerGas());
        if (newMaxFee.compareTo(cap) > 0) {
            throw new FeeCapExceededException();
        }
        TxRecord candidate = new TxRecord(record.id(), record.operationKey(), record.environment(), record.chainId(),
                record.signerRole(), record.signerAddress(), record.toAddress(), record.data(), record.gasLimit(),
                newMaxFee, newPriority, record.nonce(), null, null, null, record.state(), null, null, null, null, null,
                0, false, record.attempts(), record.version(), record.createdAtMs(), record.updatedAtMs(), null, null);
        TransactionSigning.Signed signed = TransactionSigning.sign(candidate, signer);
        String previous = String.join(",", record.allHashes());
        TxRecord updated = transitions.apply(record, TxState.SIGNED,
                "max_fee_per_gas", newMaxFee, "max_priority_fee_per_gas", newPriority,
                "raw_tx", signed.rawHex(), "tx_hash", signed.txHash(), "previous_tx_hashes", previous,
                "last_error_class", null, "missing_observations", 0);
        return send(updated);
    }

    TxRecord signAndSend(TxRecord created, Signer signer) {
        TransactionSigning.Signed signed;
        try {
            signed = TransactionSigning.sign(created, signer);
        } catch (RuntimeException e) {
            // Nothing was sent and the nonce is already consumed: flag the gap so no later nonce is issued for this signer.
            log.error("signing refused for operation {}: {}", created.operationKey(), e.getMessage());
            return transitions.apply(created, TxState.FAILED, "last_error_class", ErrorClass.SIGNER_UNAVAILABLE.name(), "nonce_gap", true);
        }
        // Persist the raw transaction and its hash BEFORE anything is sent.
        TxRecord persisted = transitions.apply(created, TxState.SIGNED, "raw_tx", signed.rawHex(), "tx_hash", signed.txHash());
        return send(persisted);
    }

    /** Sends the persisted raw transaction (the same bytes, hence the same hash, every time). Never re-signs. */
    TxRecord send(TxRecord record) {
        if (record.rawTx() == null) {
            throw new IllegalStateException("operation " + record.operationKey() + " has no persisted raw transaction");
        }
        int attempts = record.attempts() + 1;
        long submittedAt = record.submittedAtMs() != null ? record.submittedAtMs() : clock.millis();
        try {
            SendOutcome outcome = client.sendRawTransaction(record.rawTx());
            if (outcome.accepted()) {
                if (!outcome.hash().equalsIgnoreCase(record.txHash())) {
                    return transitions.apply(record, TxState.UNKNOWN, "last_error_class", ErrorClass.HASH_MISMATCH.name(), "attempts", attempts);
                }
                return transitions.apply(record, TxState.SUBMITTED, "attempts", attempts, "submitted_at_ms", submittedAt,
                        "last_error_class", null);
            }
            ErrorClass errorClass = classifier.classifyRpcError(outcome.errorMessage());
            log.warn("operation {} (nonce {}) rejected by the node as {}: {}", record.operationKey(), record.nonce(),
                    errorClass, outcome.errorMessage());
            return switch (errorClass) {
                case ALREADY_KNOWN -> transitions.apply(record, TxState.SUBMITTED, "attempts", attempts,
                        "submitted_at_ms", submittedAt, "last_error_class", null);
                case NONCE_TOO_LOW -> transitions.apply(record, TxState.UNKNOWN, "attempts", attempts,
                        "last_error_class", errorClass.name());
                case UNDERPRICED, INSUFFICIENT_FUNDS -> transitions.note(record, "attempts", attempts,
                        "last_error_class", errorClass.name());
                case PERMANENT_REJECTION -> transitions.apply(record, TxState.FAILED, "attempts", attempts,
                        "last_error_class", errorClass.name(), "nonce_gap", true);
                default -> transitions.apply(record, TxState.UNKNOWN, "attempts", attempts,
                        "last_error_class", ErrorClass.UNCLASSIFIED_RPC_ERROR.name());
            };
        } catch (ChainClientException e) {
            ErrorClass errorClass = classifier.classifyTransport(e);
            log.warn("operation {}: submission outcome unknown ({})", record.operationKey(), errorClass);
            return transitions.apply(record, TxState.UNKNOWN, "attempts", attempts, "last_error_class", errorClass.name());
        }
    }

    private TxRecord sameOrConflict(TxRecord existing, SubmitRequest request) {
        boolean same = existing.signerRole().equals(request.role().name())
                && existing.toAddress().equalsIgnoreCase(request.to())
                && existing.data().equalsIgnoreCase(request.data())
                && existing.gasLimit() == request.gasLimit();
        if (!same) {
            throw new IdempotencyConflictException(request.operationKey());
        }
        return existing;
    }

    private void validate(SubmitRequest r) {
        if (r == null || r.operationKey() == null || !KEY.matcher(r.operationKey()).matches()) {
            throw new InvalidRequestException("operationKey must be 1-190 characters of [A-Za-z0-9:_.-]");
        }
        if (r.role() == null) {
            throw new InvalidRequestException("role is required");
        }
        if (r.to() == null || !ADDRESS.matcher(r.to()).matches()) {
            throw new InvalidRequestException("to must be a 20-byte hex address");
        }
        if (allowedTargets != null && !allowedTargets.contains(r.to().toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("TARGET_NOT_ALLOWED: only the deployment manifest's escrow and token may be called");
        }
        if (r.data() == null || !HEX_DATA.matcher(r.data()).matches() || r.data().length() > MAX_DATA_HEX_CHARS) {
            throw new InvalidRequestException("data must be 0x-prefixed hex of whole bytes within the size limit");
        }
        if (r.gasLimit() < 21_000 || r.gasLimit() > properties.maxGasLimit()) {
            throw new InvalidRequestException("gasLimit must be between 21000 and " + properties.maxGasLimit());
        }
    }

    private Signer signerFor(SignerRole role) {
        if (registry == null) {
            throw new SignerUnavailableException("no development signer exists in this process");
        }
        Signer signer = registry.get(role);
        if (signer.chainId() != environment.chainId() || signer.environment() != environment.environment()) {
            throw new SignerUnavailableException("the signer is bound to a different environment or chain");
        }
        return signer;
    }

    /** {maxFeePerGas, maxPriorityFeePerGas}: twice the current base fee plus the tip, never above the configured cap. */
    private BigInteger[] suggestFees() {
        BigInteger cap = GWEI.multiply(BigInteger.valueOf(properties.maxFeeCapGwei()));
        BigInteger priority = GWEI.multiply(BigInteger.valueOf(properties.priorityFeeGwei())).min(cap);
        BigInteger maxFee = client.latestBaseFee().multiply(BigInteger.TWO).add(priority).min(cap);
        return new BigInteger[]{maxFee, priority.min(maxFee)};
    }

    private BigInteger bump(BigInteger value) {
        BigInteger bumped = value.multiply(BigInteger.valueOf(100L + properties.feeBumpPercent())).add(BigInteger.valueOf(99)).divide(BigInteger.valueOf(100));
        return bumped.max(value.add(BigInteger.ONE));
    }
}
