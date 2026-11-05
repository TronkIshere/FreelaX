package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.exception.NonceGapException;
import com.freelax.avalanchegateway.repository.TxRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Database-backed nonce allocation. The per-signer row is locked with SELECT ... FOR UPDATE, so concurrent callers (threads
 * or instances) get distinct, increasing nonces. {@link #allocate} must run inside the same database transaction that
 * inserts the transaction record: the nonce is consumed if and only if the record is persisted.
 */
public class NonceAllocator {

    private final JdbcTemplate jdbc;
    private final TxRepository repository;

    public NonceAllocator(JdbcTemplate jdbc, TxRepository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
    }

    /**
     * @param chainPendingNonce the node's pending transaction count for the signer, read before the database transaction;
     *                          used only as a lower bound (it never lowers the stored value)
     */
    public long allocate(String environment, long chainId, String signerAddress, long chainPendingNonce, long nowMs) {
        if (repository.hasNonceGap(environment, chainId, signerAddress)) {
            throw new NonceGapException(signerAddress);
        }
        // Upsert first: this takes the row's exclusive lock directly. Locking a row that does not exist yet (SELECT ... FOR UPDATE)
        // takes a gap lock, and two first-time allocators then deadlock on the insert.
        jdbc.update("INSERT INTO avalanche_nonce (environment, chain_id, signer_address, next_nonce, updated_at_ms) VALUES (?,?,?,?,?)"
                        + " ON DUPLICATE KEY UPDATE updated_at_ms = updated_at_ms",
                environment, chainId, signerAddress, chainPendingNonce, nowMs);
        Long stored = lock(environment, chainId, signerAddress);
        if (stored == null) {
            throw new IllegalStateException("the nonce row could not be locked");
        }
        long nonce = Math.max(stored, chainPendingNonce);
        jdbc.update("UPDATE avalanche_nonce SET next_nonce = ?, updated_at_ms = ? WHERE environment = ? AND chain_id = ? AND signer_address = ?",
                nonce + 1, nowMs, environment, chainId, signerAddress);
        return nonce;
    }

    public Long peekNext(String environment, long chainId, String signerAddress) {
        List<Long> rows = jdbc.queryForList("SELECT next_nonce FROM avalanche_nonce WHERE environment = ? AND chain_id = ? AND signer_address = ?",
                Long.class, environment, chainId, signerAddress);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long lock(String environment, long chainId, String signerAddress) {
        List<Long> rows = jdbc.queryForList(
                "SELECT next_nonce FROM avalanche_nonce WHERE environment = ? AND chain_id = ? AND signer_address = ? FOR UPDATE",
                Long.class, environment, chainId, signerAddress);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
