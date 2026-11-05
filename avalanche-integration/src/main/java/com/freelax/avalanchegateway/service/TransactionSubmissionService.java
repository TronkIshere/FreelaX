package com.freelax.avalanchegateway.service;

import com.freelax.avalanchegateway.dto.request.SubmitRequest;
import com.freelax.avalanchegateway.entity.TxRecord;
import com.freelax.avalanchegateway.signer.Signer;

/**
 * Submits backend-signed transactions safely: one transaction per business operation, the raw transaction and hash are
 * persisted before anything is sent, a timeout is never a failure and never a second send, and nothing is ever re-signed
 * with a new nonce. Exists only where development signers exist (LOCAL_SIMULATION); there is no signing path for Fuji.
 * What to submit is decided by the caller (business authorization is not this service's job); value is always zero.
 */
public interface TransactionSubmissionService {

    /** Idempotent by operation key: a repeated call returns the existing record and never allocates a new nonce. */
    TxRecord submit(SubmitRequest request);

    /**
     * Replaces the current attempt with a higher-fee transaction for the same nonce. The earlier hashes are kept: any of
     * them may still be the one that mines.
     */
    TxRecord speedUp(String operationKey);

    /** Signs a freshly created record, persists the raw transaction and sends it. */
    TxRecord signAndSend(TxRecord created, Signer signer);

    /** Sends the persisted raw transaction (the same bytes, hence the same hash, every time). Never re-signs. */
    TxRecord send(TxRecord record);
}
