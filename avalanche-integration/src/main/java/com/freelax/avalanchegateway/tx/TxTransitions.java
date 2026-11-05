package com.freelax.avalanchegateway.tx;

import com.freelax.avalanchegateway.entity.TxRecord;
import com.freelax.avalanchegateway.entity.TxState;
import com.freelax.avalanchegateway.repository.TxRepository;

import java.time.Clock;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The only place that changes a record's state. Illegal transitions are programming errors and throw. */
public class TxTransitions {

    private final TxRepository repository;
    private final Clock clock;

    public TxTransitions(TxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    static boolean allowed(TxState from, TxState to) {
        if (from.terminal()) {
            return false;
        }
        Set<TxState> targets = switch (from) {
            case CREATED -> EnumSet.of(TxState.CREATED, TxState.SIGNED, TxState.FAILED);
            case CONFIRMED -> EnumSet.of(TxState.CONFIRMED, TxState.FINALIZED);
            default -> EnumSet.of(TxState.SIGNED, TxState.SUBMITTED, TxState.PENDING, TxState.CONFIRMED, TxState.REVERTED,
                    TxState.FAILED, TxState.REPLACED, TxState.UNKNOWN);
        };
        return targets.contains(to) || (from == to);
    }

    /** Moves to {@code to} and sets the given columns; returns the stored row (the latest one if another writer won). */
    public TxRecord apply(TxRecord record, TxState to, Object... columnsAndValues) {
        if (record.state().terminal()) {
            return record;
        }
        if (!allowed(record.state(), to)) {
            throw new IllegalStateException("illegal transition " + record.state() + " -> " + to);
        }
        Map<String, Object> columns = cols(columnsAndValues);
        columns.put("state", to.name());
        columns.put("last_checked_at_ms", clock.millis());
        repository.update(record, columns, clock.millis());
        return repository.findById(record.id()).orElseThrow();
    }

    /** Records a fact (an error class, an observation counter) without changing the state. */
    public TxRecord note(TxRecord record, Object... columnsAndValues) {
        if (record.state().terminal()) {
            return record;
        }
        Map<String, Object> columns = cols(columnsAndValues);
        columns.put("last_checked_at_ms", clock.millis());
        repository.update(record, columns, clock.millis());
        return repository.findById(record.id()).orElseThrow();
    }

    private static Map<String, Object> cols(Object[] kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }
}
