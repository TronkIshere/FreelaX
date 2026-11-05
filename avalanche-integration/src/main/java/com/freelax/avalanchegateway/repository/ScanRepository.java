package com.freelax.avalanchegateway.repository;

import com.freelax.avalanchegateway.entity.ScannedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Cursor and events. A chunk's events and the cursor move in ONE database transaction, so a crash can never leave the
 * cursor ahead of unrecorded events. Duplicates are suppressed by the unique (environment, chain, tx hash, log index) key,
 * and the cursor can only move forward.
 */
public class ScanRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ScanRepository(JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Optional<Long> cursor(String environment, long chainId, String contract) {
        List<Long> rows = jdbc.queryForList("SELECT last_scanned_block FROM avalanche_scan_cursor WHERE environment = ? AND chain_id = ? AND contract_address = ?",
                Long.class, environment, chainId, contract);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** @return the number of events that were new (duplicates are ignored) */
    public int saveChunk(String environment, long chainId, String contract, List<ScannedEvent> events, long newCursor) {
        Integer inserted = transactions.execute(status -> {
            int fresh = 0;
            for (ScannedEvent e : events) {
                fresh += jdbc.update("INSERT IGNORE INTO avalanche_event (environment, chain_id, contract_address, tx_hash, log_index,"
                                + " block_number, block_hash, topic0, topics, data, event_name, created_at_ms) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                        e.environment(), e.chainId(), e.contractAddress(), e.txHash(), e.logIndex(), e.blockNumber(), e.blockHash(),
                        e.topic0(), String.join(",", e.topics()), e.data(), e.eventName(), clock.millis());
            }
            jdbc.update("INSERT INTO avalanche_scan_cursor (environment, chain_id, contract_address, last_scanned_block, updated_at_ms)"
                            + " VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE last_scanned_block = GREATEST(last_scanned_block, VALUES(last_scanned_block)),"
                            + " updated_at_ms = VALUES(updated_at_ms)",
                    environment, chainId, contract, newCursor, clock.millis());
            return fresh;
        });
        return inserted == null ? 0 : inserted;
    }

    /** Events of one escrow (the escrow id is the first indexed topic), in chain order. */
    public List<ScannedEvent> eventsForEscrow(String environment, long chainId, String contract, String escrowId) {
        return events(environment, chainId, contract).stream()
                .filter(e -> e.topics().size() > 1 && e.topics().get(1).equalsIgnoreCase(escrowId))
                .toList();
    }

    public List<ScannedEvent> events(String environment, long chainId, String contract) {
        return jdbc.query("SELECT * FROM avalanche_event WHERE environment = ? AND chain_id = ? AND contract_address = ? ORDER BY block_number, log_index",
                (rs, i) -> new ScannedEvent(rs.getString("environment"), rs.getLong("chain_id"), rs.getString("contract_address"),
                        rs.getString("tx_hash"), rs.getInt("log_index"), rs.getLong("block_number"), rs.getString("block_hash"),
                        rs.getString("topic0"), Arrays.asList(rs.getString("topics").split(",")), rs.getString("data"),
                        rs.getString("event_name")),
                environment, chainId, contract);
    }
}
