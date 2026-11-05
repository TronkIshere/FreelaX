package com.freelax.avalanchegateway.repository;

import com.freelax.avalanchegateway.entity.TxRecord;
import com.freelax.avalanchegateway.entity.TxState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Plain JDBC access. Every query is scoped by environment and chain id, so rows of another environment are invisible. */
public class TxRepository {

    private static final String SELECT = "SELECT * FROM avalanche_tx ";

    private final JdbcTemplate jdbc;

    public TxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TxRecord> find(String environment, long chainId, String operationKey) {
        return jdbc.query(SELECT + "WHERE environment = ? AND chain_id = ? AND operation_key = ?", MAPPER,
                environment, chainId, operationKey).stream().findFirst();
    }

    public Optional<TxRecord> findById(long id) {
        return jdbc.query(SELECT + "WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    /** Non-terminal records of exactly this environment and chain, oldest first. */
    public List<TxRecord> inFlight(String environment, long chainId) {
        return jdbc.query(SELECT + "WHERE environment = ? AND chain_id = ? AND state NOT IN ('FINALIZED','REVERTED','FAILED','REPLACED') ORDER BY id",
                MAPPER, environment, chainId);
    }

    public boolean hasNonceGap(String environment, long chainId, String signerAddress) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM avalanche_tx WHERE environment = ? AND chain_id = ? AND signer_address = ? AND nonce_gap = TRUE",
                Integer.class, environment, chainId, signerAddress);
        return count != null && count > 0;
    }

    public long insertCreated(String operationKey, String environment, long chainId, String signerRole, String signerAddress,
                              String to, String data, long gasLimit, BigInteger maxFee, BigInteger maxPriorityFee,
                              long nonce, long nowMs) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO avalanche_tx (operation_key, environment, chain_id, signer_role, signer_address, to_address, data,"
                            + " gas_limit, max_fee_per_gas, max_priority_fee_per_gas, nonce, state, created_at_ms, updated_at_ms)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setString(i++, operationKey);
            ps.setString(i++, environment);
            ps.setLong(i++, chainId);
            ps.setString(i++, signerRole);
            ps.setString(i++, signerAddress);
            ps.setString(i++, to);
            ps.setString(i++, data);
            ps.setLong(i++, gasLimit);
            ps.setBigDecimal(i++, new BigDecimal(maxFee));
            ps.setBigDecimal(i++, new BigDecimal(maxPriorityFee));
            ps.setLong(i++, nonce);
            ps.setString(i++, TxState.CREATED.name());
            ps.setLong(i++, nowMs);
            ps.setLong(i, nowMs);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /**
     * Optimistic update: succeeds only if the row still has the version the caller read. Returns false on a concurrent
     * change, in which case the caller reloads and decides again.
     */
    public boolean update(TxRecord expected, Map<String, Object> columns, long nowMs) {
        Map<String, Object> all = new LinkedHashMap<>(columns);
        all.put("updated_at_ms", nowMs);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE avalanche_tx SET version = version + 1");
        all.forEach((column, value) -> {
            sql.append(", ").append(column).append(" = ?");
            args.add(value instanceof BigInteger big ? new BigDecimal(big) : value);
        });
        sql.append(" WHERE id = ? AND version = ?");
        args.add(expected.id());
        args.add(expected.version());
        return jdbc.update(sql.toString(), args.toArray()) == 1;
    }

    private static final RowMapper<TxRecord> MAPPER = TxRepository::map;

    private static TxRecord map(ResultSet rs, int row) throws SQLException {
        return new TxRecord(
                rs.getLong("id"), rs.getString("operation_key"), rs.getString("environment"), rs.getLong("chain_id"),
                rs.getString("signer_role"), rs.getString("signer_address"), rs.getString("to_address"), rs.getString("data"),
                rs.getLong("gas_limit"), rs.getBigDecimal("max_fee_per_gas").toBigIntegerExact(),
                rs.getBigDecimal("max_priority_fee_per_gas").toBigIntegerExact(), rs.getLong("nonce"),
                rs.getString("raw_tx"), rs.getString("tx_hash"), rs.getString("previous_tx_hashes"),
                TxState.valueOf(rs.getString("state")), nullableLong(rs, "block_number"), rs.getString("block_hash"),
                nullableInt(rs, "receipt_status"), rs.getString("last_error_class"), rs.getString("replaced_by_tx_hash"),
                rs.getInt("missing_observations"), rs.getBoolean("nonce_gap"), rs.getInt("attempts"), rs.getLong("version"),
                rs.getLong("created_at_ms"), rs.getLong("updated_at_ms"), nullableLong(rs, "submitted_at_ms"),
                nullableLong(rs, "last_checked_at_ms"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
