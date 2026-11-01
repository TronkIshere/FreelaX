package com.freelax.avalanchegateway.scan;

import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.LogEntry;
import com.freelax.avalanchegateway.chain.LogRangeExceededException;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bounded, restart-safe scan of one contract's logs. It never scans past the safe head (confirmation depth on
 * LOCAL_SIMULATION, the finalized block on Fuji), uses the provider's range limit and halves it on refusal, accepts only logs
 * from the configured contract with a registered topic, and records each chunk's events and the cursor in one transaction.
 * An empty answer is only trusted below the safe head. The WebSocket (hints) can trigger a scan but never supplies events.
 */
public class LogScanner {

    private static final Logger log = LoggerFactory.getLogger(LogScanner.class);
    private static final int MAX_CHUNKS_PER_PASS = 200;

    public record ScanResult(long fromBlock, long toBlock, int fetchedLogs, int acceptedLogs, int newEvents, long cursor, int chunks) {
    }

    private final ChainClient client;
    private final ValidatedEnvironment environment;
    private final ScanRepository repository;
    private final EventDecoder decoder;
    private final String contract;
    private final long startBlock;
    private final int confirmations;
    private long range;

    public LogScanner(ChainClient client, ValidatedEnvironment environment, ScanRepository repository, EventDecoder decoder,
                      String contractAddress, long startBlock, int confirmations, ProviderProfile profile, long rangeOverride) {
        this.client = client;
        this.environment = environment;
        this.repository = repository;
        this.decoder = decoder;
        this.contract = contractAddress.toLowerCase(Locale.ROOT);
        this.startBlock = startBlock;
        this.confirmations = confirmations;
        this.range = rangeOverride > 0 ? Math.min(rangeOverride, profile.maxLogRange()) : profile.maxLogRange();
    }

    public long currentRange() {
        return range;
    }

    /** One bounded pass: scans from the stored cursor up to the safe head. Single flight. */
    public synchronized ScanResult scanOnce() {
        String env = environment.environment().name();
        long cursor = repository.cursor(env, environment.chainId(), contract).orElse(startBlock - 1);
        long first = cursor + 1;
        long safeHead = safeHead();
        int fetched = 0;
        int accepted = 0;
        int fresh = 0;
        int chunks = 0;
        while (cursor < safeHead && chunks < MAX_CHUNKS_PER_PASS) {
            long from = cursor + 1;
            long to = Math.min(cursor + range, safeHead);
            List<LogEntry> logs;
            try {
                logs = client.logs(contract, from, to);
            } catch (LogRangeExceededException e) {
                if (range <= 1) {
                    throw e;
                }
                range = Math.max(1, range / 2);
                log.warn("the provider refused the log range; halving it to {}", range);
                continue;
            }
            List<ScannedEvent> events = accept(logs, from, to, env);
            fetched += logs.size();
            accepted += events.size();
            fresh += repository.saveChunk(env, environment.chainId(), contract, events, to);
            cursor = to;
            chunks++;
        }
        return new ScanResult(first, cursor, fetched, accepted, fresh, cursor, chunks);
    }

    private long safeHead() {
        if (environment.environment() == AvalancheEnvironment.LOCAL_SIMULATION) {
            return client.latestBlockNumber() - confirmations;
        }
        return client.finalizedBlockNumber();
    }

    private List<ScannedEvent> accept(List<LogEntry> logs, long from, long to, String env) {
        List<ScannedEvent> events = new ArrayList<>();
        for (LogEntry entry : logs) {
            boolean ours = contract.equalsIgnoreCase(entry.address());
            boolean inRange = entry.blockNumber() >= from && entry.blockNumber() <= to;
            boolean known = !entry.topics().isEmpty() && decoder.known(entry.topics().get(0));
            if (entry.removed() || !ours || !inRange || !known) {
                continue;
            }
            String topic0 = entry.topics().get(0).toLowerCase(Locale.ROOT);
            events.add(new ScannedEvent(env, environment.chainId(), contract, entry.txHash().toLowerCase(Locale.ROOT), entry.logIndex(),
                    entry.blockNumber(), entry.blockHash(), topic0, entry.topics(), entry.data(), decoder.name(topic0)));
        }
        return events;
    }
}
