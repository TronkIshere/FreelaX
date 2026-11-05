package com.freelax.avalanchegateway.service.impl;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.SendOutcome;
import com.freelax.avalanchegateway.config.TxProperties;
import com.freelax.avalanchegateway.dto.request.SubmitRequest;
import com.freelax.avalanchegateway.entity.TxRecord;
import com.freelax.avalanchegateway.entity.TxState;
import com.freelax.avalanchegateway.env.EnvironmentGuard;
import com.freelax.avalanchegateway.exception.ChainClientException;
import com.freelax.avalanchegateway.exception.FeeCapExceededException;
import com.freelax.avalanchegateway.exception.IdempotencyConflictException;
import com.freelax.avalanchegateway.exception.InvalidRequestException;
import com.freelax.avalanchegateway.exception.SignerUnavailableException;
import com.freelax.avalanchegateway.repository.TxRepository;
import com.freelax.avalanchegateway.service.TransactionSubmissionService;
import com.freelax.avalanchegateway.signer.Signer;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import com.freelax.avalanchegateway.tx.ErrorClass;
import com.freelax.avalanchegateway.tx.ErrorClassifier;
import com.freelax.avalanchegateway.tx.NonceAllocator;
import com.freelax.avalanchegateway.tx.TransactionSigning;
import com.freelax.avalanchegateway.tx.TxTransitions;
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
import java.util.Set;
import java.util.regex.Pattern;

/** Created by the transaction configuration only where development signers exist; it is not component-scanned. */
public class TransactionSubmissionServiceImpl implements TransactionSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionSubmissionServiceImpl.class);
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9:_.\\-]{1,190}");
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final Pattern HEX_DATA = Pattern.compile("0x([0-9a-fA-F]{2})*");
    private static final int MAX_DATA_HEX_CHARS = 262_144;
    private static final long MIN_GAS_LIMIT = 21_000;
    private static final int MAX_ALLOCATION_ATTEMPTS = 5;
    private static final BigInteger GWEI = BigInteger.valueOf(1_000_000_000L);

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
    private final Set<String> allowedTargets;

    public TransactionSubmissionServiceImpl(ChainClient client, ValidatedEnvironment environment, SignerRegistry registry,
                                            TxRepository repository, NonceAllocator allocator, TransactionTemplate transactions,
                                            TxProperties properties, ErrorClassifier classifier, TxTransitions transitions, Clock clock,
                                            Set<String> allowedTargets) {
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

    @Override
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
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.execute(status -> insertCreated(request, signer, chainFloor, fees));
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= MAX_ALLOCATION_ATTEMPTS) {
                    throw e;
                }
                log.warn("nonce allocation lock conflict, retrying (attempt {})", attempt);
            }
        }
    }

    private TxRecord insertCreated(SubmitRequest request, Signer signer, long chainFloor, BigInteger[] fees) {
        String env = environment.environment().name();
        long chainId = environment.chainId();
        long nonce = allocator.allocate(env, chainId, signer.address(), chainFloor, clock.millis());
        long id = repository.insertCreated(request.operationKey(), env, chainId, request.role().name(), signer.address(),
                request.to().toLowerCase(Locale.ROOT), request.data().toLowerCase(Locale.ROOT), request.gasLimit(),
                fees[0], fees[1], nonce, clock.millis());
        return repository.findById(id).orElseThrow();
    }

    @Override
    public TxRecord speedUp(String operationKey) {
        TxRecord record = repository.find(environment.environment().name(), environment.chainId(), operationKey)
                .orElseThrow(() -> new InvalidRequestException("unknown operation " + operationKey));
        if (!canBeSpedUp(record)) {
            throw new IllegalStateException("a " + record.state() + " operation cannot be sped up");
        }
        Signer signer = signerFor(SignerRole.valueOf(record.signerRole()));
        BigInteger cap = feeCap();
        BigInteger newMaxFee = bump(record.maxFeePerGas());
        BigInteger newPriority = bump(record.maxPriorityFeePerGas());
        if (newMaxFee.compareTo(cap) > 0) {
            throw new FeeCapExceededException();
        }
        TransactionSigning.Signed signed = TransactionSigning.sign(record.withReplacementFees(newMaxFee, newPriority), signer);
        String previous = String.join(",", record.allHashes());
        TxRecord updated = transitions.apply(record, TxState.SIGNED,
                "max_fee_per_gas", newMaxFee, "max_priority_fee_per_gas", newPriority,
                "raw_tx", signed.rawHex(), "tx_hash", signed.txHash(), "previous_tx_hashes", previous,
                "last_error_class", null, "missing_observations", 0);
        return send(updated);
    }

    private static boolean canBeSpedUp(TxRecord record) {
        return !record.state().terminal() && record.state() != TxState.CREATED && record.state() != TxState.CONFIRMED;
    }

    @Override
    public TxRecord signAndSend(TxRecord created, Signer signer) {
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

    @Override
    public TxRecord send(TxRecord record) {
        if (record.rawTx() == null) {
            throw new IllegalStateException("operation " + record.operationKey() + " has no persisted raw transaction");
        }
        int attempts = record.attempts() + 1;
        long submittedAt = record.submittedAtMs() != null ? record.submittedAtMs() : clock.millis();
        try {
            SendOutcome outcome = client.sendRawTransaction(record.rawTx());
            return outcome.accepted() ? afterAcceptance(record, outcome, attempts, submittedAt) : afterRejection(record, outcome, attempts, submittedAt);
        } catch (ChainClientException e) {
            ErrorClass errorClass = classifier.classifyTransport(e);
            log.warn("operation {}: submission outcome unknown ({})", record.operationKey(), errorClass);
            return transitions.apply(record, TxState.UNKNOWN, "attempts", attempts, "last_error_class", errorClass.name());
        }
    }

    private TxRecord afterAcceptance(TxRecord record, SendOutcome outcome, int attempts, long submittedAt) {
        if (!outcome.hash().equalsIgnoreCase(record.txHash())) {
            return transitions.apply(record, TxState.UNKNOWN, "last_error_class", ErrorClass.HASH_MISMATCH.name(), "attempts", attempts);
        }
        return transitions.apply(record, TxState.SUBMITTED, "attempts", attempts, "submitted_at_ms", submittedAt, "last_error_class", null);
    }

    private TxRecord afterRejection(TxRecord record, SendOutcome outcome, int attempts, long submittedAt) {
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
    }

    private static TxRecord sameOrConflict(TxRecord existing, SubmitRequest request) {
        boolean same = existing.signerRole().equals(request.role().name())
                && existing.toAddress().equalsIgnoreCase(request.to())
                && existing.data().equalsIgnoreCase(request.data())
                && existing.gasLimit() == request.gasLimit();
        if (!same) {
            throw new IdempotencyConflictException(request.operationKey());
        }
        return existing;
    }

    private void validate(SubmitRequest request) {
        if (request == null || request.operationKey() == null || !KEY.matcher(request.operationKey()).matches()) {
            throw new InvalidRequestException("operationKey must be 1-190 characters of [A-Za-z0-9:_.-]");
        }
        if (request.role() == null) {
            throw new InvalidRequestException("role is required");
        }
        validateTarget(request.to());
        if (request.data() == null || !HEX_DATA.matcher(request.data()).matches() || request.data().length() > MAX_DATA_HEX_CHARS) {
            throw new InvalidRequestException("data must be 0x-prefixed hex of whole bytes within the size limit");
        }
        if (request.gasLimit() < MIN_GAS_LIMIT || request.gasLimit() > properties.maxGasLimit()) {
            throw new InvalidRequestException("gasLimit must be between " + MIN_GAS_LIMIT + " and " + properties.maxGasLimit());
        }
    }

    private void validateTarget(String to) {
        if (to == null || !ADDRESS.matcher(to).matches()) {
            throw new InvalidRequestException("to must be a 20-byte hex address");
        }
        if (allowedTargets != null && !allowedTargets.contains(to.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException("TARGET_NOT_ALLOWED: only the deployment manifest's escrow and token may be called");
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

    private BigInteger feeCap() {
        return GWEI.multiply(BigInteger.valueOf(properties.maxFeeCapGwei()));
    }

    /** {maxFeePerGas, maxPriorityFeePerGas}: twice the current base fee plus the tip, never above the configured cap. */
    private BigInteger[] suggestFees() {
        BigInteger cap = feeCap();
        BigInteger priority = GWEI.multiply(BigInteger.valueOf(properties.priorityFeeGwei())).min(cap);
        BigInteger maxFee = client.latestBaseFee().multiply(BigInteger.TWO).add(priority).min(cap);
        return new BigInteger[]{maxFee, priority.min(maxFee)};
    }

    private BigInteger bump(BigInteger value) {
        BigInteger bumped = value.multiply(BigInteger.valueOf(100L + properties.feeBumpPercent())).add(BigInteger.valueOf(99)).divide(BigInteger.valueOf(100));
        return bumped.max(value.add(BigInteger.ONE));
    }
}
