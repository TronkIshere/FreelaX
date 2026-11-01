package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.ChainClientException;
import com.freelax.avalanchegateway.chain.TxReceipt;
import com.freelax.avalanchegateway.config.TxProperties;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.signer.Signer;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.signer.SignerRole;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reconciles in-flight records with the chain. Rules (Phase 2 plan 06): a lookup error or a null answer is never
 * evidence and never changes the state; CONFIRMED needs receipt status 1 and the expected target; REVERTED is a receipt
 * with status 0; REPLACED needs receipt null and by-hash null for every known hash, the nonce consumed, the grace period
 * elapsed and repeated observations; otherwise the same raw transaction is rebroadcast. Only rows of this environment
 * and chain are touched. WebSocket hints (M4) may trigger a pass but can never set a state.
 */
public class TxTracker {

    private static final Logger log = LoggerFactory.getLogger(TxTracker.class);

    private final ChainClient client;
    private final ValidatedEnvironment environment;
    private final TxRepository repository;
    private final TxTransitions transitions;
    private final SubmissionService submission;
    private final SignerRegistry registry;
    private final ErrorClassifier classifier;
    private final TxProperties properties;
    private final Clock clock;
    private final ChainClient secondary;

    /** {@code submission} and {@code registry} are null where no signer exists; signing and resending are then skipped. */
    public TxTracker(ChainClient client, ValidatedEnvironment environment, TxRepository repository, TxTransitions transitions,
                     SubmissionService submission, SignerRegistry registry, ErrorClassifier classifier,
                     TxProperties properties, Clock clock, ChainClient secondary) {
        this.client = client;
        this.environment = environment;
        this.repository = repository;
        this.transitions = transitions;
        this.submission = submission;
        this.registry = registry;
        this.classifier = classifier;
        this.properties = properties;
        this.clock = clock;
        this.secondary = secondary;
    }

    /** One pass over every in-flight record of this environment and chain; a failing record does not stop the pass. */
    public List<TxRecord> reconcileInFlight() {
        List<TxRecord> results = new ArrayList<>();
        for (TxRecord record : repository.inFlight(environment.environment().name(), environment.chainId())) {
            try {
                results.add(reconcile(record));
            } catch (RuntimeException e) {
                log.warn("reconciliation of operation {} failed: {}", record.operationKey(), e.getClass().getSimpleName());
            }
        }
        return results;
    }

    public TxRecord reconcile(long id) {
        return reconcile(repository.findById(id).orElseThrow());
    }

    public TxRecord reconcile(TxRecord record) {
        if (!record.environment().equals(environment.environment().name()) || record.chainId() != environment.chainId()) {
            return record;
        }
        if (record.state().terminal()) {
            return record;
        }
        return switch (record.state()) {
            case CREATED -> resumeCreated(record);
            case CONFIRMED -> checkFinality(record);
            default -> observe(record);
        };
    }

    private TxRecord resumeCreated(TxRecord record) {
        if (submission == null || registry == null) {
            return record;
        }
        Signer signer = registry.get(SignerRole.valueOf(record.signerRole()));
        if (!signer.address().equalsIgnoreCase(record.signerAddress())) {
            // The signer that owned this nonce was ephemeral and is gone; the address will never send again, so no gap matters.
            return transitions.apply(record, TxState.FAILED, "last_error_class", ErrorClass.SIGNER_UNAVAILABLE.name());
        }
        return submission.signAndSend(record, signer);
    }

    private TxRecord observe(TxRecord record) {
        List<String> hashes = record.allHashes();
        try {
            for (String hash : hashes) {
                Optional<TxReceipt> receipt = client.receipt(hash);
                if (receipt.isPresent()) {
                    return fromReceipt(record, hash, receipt.get());
                }
            }
            for (String hash : hashes) {
                if (client.transactionExists(hash)) {
                    return transitions.apply(record, TxState.PENDING, "missing_observations", 0, "last_error_class", null);
                }
            }
            TxRecord crossChecked = askSecondary(record, hashes);
            return crossChecked != null ? crossChecked : missing(record);
        } catch (ChainClientException e) {
            return transitions.note(record, "last_error_class", classifier.classifyTransport(e).name());
        }
    }

    /** A null from one provider is not evidence: before treating the transaction as missing, ask the second provider of the same environment. */
    private TxRecord askSecondary(TxRecord record, List<String> hashes) {
        if (secondary == null) {
            return null;
        }
        for (String hash : hashes) {
            try {
                Optional<TxReceipt> receipt = secondary.receipt(hash);
                if (receipt.isPresent()) {
                    return fromReceipt(record, hash, receipt.get());
                }
            } catch (ChainClientException e) {
                log.warn("secondary provider lookup failed: {}", e.getClass().getSimpleName());
            }
        }
        return null;
    }

    private TxRecord fromReceipt(TxRecord record, String hash, TxReceipt receipt) {
        List<String> others = new ArrayList<>(record.allHashes());
        others.remove(hash);
        String previous = String.join(",", others);
        if (!receipt.success()) {
            return transitions.apply(record, TxState.REVERTED, "tx_hash", hash, "previous_tx_hashes", previous,
                    "block_number", receipt.blockNumber(), "block_hash", receipt.blockHash(), "receipt_status", 0,
                    "last_error_class", null);
        }
        if (receipt.to() == null || !receipt.to().equalsIgnoreCase(record.toAddress())) {
            return transitions.apply(record, TxState.UNKNOWN, "last_error_class", ErrorClass.RECEIPT_TARGET_MISMATCH.name());
        }
        TxRecord confirmed = transitions.apply(record, TxState.CONFIRMED, "tx_hash", hash, "previous_tx_hashes", previous,
                "block_number", receipt.blockNumber(), "block_hash", receipt.blockHash(), "receipt_status", 1,
                "last_error_class", null, "missing_observations", 0);
        return checkFinality(confirmed);
    }

    private TxRecord missing(TxRecord record) {
        long reference = record.submittedAtMs() != null ? record.submittedAtMs() : record.updatedAtMs();
        boolean graceElapsed = clock.millis() - reference >= properties.replaceGraceMs();
        long latestNonce = client.transactionCount(record.signerAddress(), DefaultBlockParameterName.LATEST);
        if (latestNonce > record.nonce()) {
            int observations = record.missingObservations() + 1;
            if (graceElapsed && observations >= properties.replaceObservations()) {
                return transitions.apply(record, TxState.REPLACED, "missing_observations", observations);
            }
            return transitions.note(record, "missing_observations", observations);
        }
        if (graceElapsed && submission != null) {
            return submission.send(record);
        }
        return transitions.note(record);
    }

    private TxRecord checkFinality(TxRecord record) {
        try {
            long block = record.blockNumber();
            boolean finalized = environment.environment() == AvalancheEnvironment.LOCAL_SIMULATION
                    ? client.latestBlockNumber() >= block + properties.confirmations()
                    : client.finalizedBlockNumber() >= block;
            return finalized ? transitions.apply(record, TxState.FINALIZED) : transitions.note(record);
        } catch (ChainClientException e) {
            return transitions.note(record, "last_error_class", classifier.classifyTransport(e).name());
        }
    }
}
