package com.freelax.avalanchegateway.service;

import com.freelax.avalanchegateway.entity.TxRecord;

import java.util.List;

/**
 * Reconciles in-flight records with the chain. A lookup error or a null answer is never evidence and never changes the state.
 * Only rows of this environment and chain are touched. WebSocket hints may trigger a pass but can never set a state.
 */
public interface TransactionReconcileService {

    /** One pass over every in-flight record of this environment and chain; a failing record does not stop the pass. */
    List<TxRecord> reconcileInFlight();

    TxRecord reconcile(long id);

    TxRecord reconcile(TxRecord record);
}
