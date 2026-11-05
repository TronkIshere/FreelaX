package com.freelax.avalanchegateway.contract;

import com.freelax.avalanchegateway.entity.ScannedEvent;
import com.freelax.avalanchegateway.repository.ScanRepository;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;

import java.util.List;
import java.util.Locale;

/**
 * Compares the status implied by an escrow's scanned events with the status read from the contract. On a mismatch the contract
 * wins for custody, status, deadlines and counters (decision D4): the result is a report for an operator or an alert, never an
 * automatic change. An empty event list is only a finding when the chain disagrees, never proof of absence.
 */
public class EscrowReconciler {

    public record Result(String escrowId, int expectedStatus, int chainStatus, boolean match, int eventCount) {
    }

    private final EscrowReader reader;
    private final ScanRepository repository;
    private final ValidatedEnvironment environment;
    private final String contract;

    public EscrowReconciler(EscrowReader reader, ScanRepository repository, ValidatedEnvironment environment, String contractAddress) {
        this.reader = reader;
        this.repository = repository;
        this.environment = environment;
        this.contract = contractAddress.toLowerCase(Locale.ROOT);
    }

    public Result reconcile(String escrowId) {
        List<ScannedEvent> events = repository.eventsForEscrow(environment.environment().name(), environment.chainId(), contract, escrowId);
        int expected = 0;
        for (ScannedEvent event : events) {
            expected = switch (event.eventName()) {
                case "Funded" -> 1;
                case "WorkSubmitted" -> 2;
                case "RevisionRequested" -> 3;
                case "DisputeOpened" -> 4;
                case "Released" -> 5;
                case "Refunded" -> 6;
                default -> expected;
            };
        }
        int onChain = reader.read(escrowId).status();
        return new Result(escrowId, expected, onChain, expected == onChain, events.size());
    }
}
