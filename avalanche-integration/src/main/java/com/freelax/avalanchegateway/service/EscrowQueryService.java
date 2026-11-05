package com.freelax.avalanchegateway.service;

import com.freelax.avalanchegateway.dto.request.WalletSignatureRequest;
import com.freelax.avalanchegateway.dto.response.AddressValidation;
import com.freelax.avalanchegateway.dto.response.BalanceResponse;
import com.freelax.avalanchegateway.dto.response.ConfigResponse;
import com.freelax.avalanchegateway.dto.response.EscrowResponse;
import com.freelax.avalanchegateway.dto.response.HashStatusResponse;
import com.freelax.avalanchegateway.dto.response.IssuerAlertsResponse;
import com.freelax.avalanchegateway.dto.response.TokenTransferResponse;
import com.freelax.avalanchegateway.dto.response.TransactionResponse;
import com.freelax.avalanchegateway.dto.response.WalletSignatureResponse;

/** Read-only questions about the deployment, the escrows and the transactions. Nothing here signs or sends. */
public interface EscrowQueryService {

    ConfigResponse config();

    AddressValidation validateAddress(String address);

    /** Wallet linking (A8): verifies that the address signed the message with EIP-191 personal_sign. */
    WalletSignatureResponse verifyWalletSignature(WalletSignatureRequest request);

    BalanceResponse balance(String address);

    EscrowResponse escrow(String escrowId);

    /** P3-C4: which issuer controls block this escrow; never a recovery path (O-6). */
    IssuerAlertsResponse alerts(String escrowId);

    TransactionResponse transaction(String operationKey);

    /**
     * Status of a transaction broadcast by a user's own wallet, derived live from the chain, nothing stored. A receipt counts
     * only when its target is one of the verified manifest contracts; any other target is reported UNKNOWN.
     */
    HashStatusResponse statusByHash(String txHash);

    /**
     * Verifies that the transaction moved exactly {@code amount} base units of the manifest token from {@code from} to
     * {@code to}. On-chain evidence only, never a fiat payout confirmation.
     */
    TokenTransferResponse verifyTokenTransfer(String txHash, String from, String to, String amount);
}
