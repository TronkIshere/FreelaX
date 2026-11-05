package com.freelax.avalanchegateway.controller;

import com.freelax.avalanchegateway.dto.request.ActionRequest;
import com.freelax.avalanchegateway.dto.request.FaucetRequest;
import com.freelax.avalanchegateway.dto.request.WalletSignatureRequest;
import com.freelax.avalanchegateway.dto.response.AddressValidation;
import com.freelax.avalanchegateway.dto.response.BalanceResponse;
import com.freelax.avalanchegateway.dto.response.ConfigResponse;
import com.freelax.avalanchegateway.dto.response.EscrowResponse;
import com.freelax.avalanchegateway.dto.response.FaucetResponse;
import com.freelax.avalanchegateway.dto.response.HashStatusResponse;
import com.freelax.avalanchegateway.dto.response.IssuerAlertsResponse;
import com.freelax.avalanchegateway.dto.response.PreparedActionResponse;
import com.freelax.avalanchegateway.dto.response.TokenTransferResponse;
import com.freelax.avalanchegateway.dto.response.TransactionResponse;
import com.freelax.avalanchegateway.dto.response.WalletSignatureResponse;
import com.freelax.avalanchegateway.service.EscrowActionService;
import com.freelax.avalanchegateway.service.EscrowQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The gateway API. Every response names its environment and says the money is simulated; POSTs need the internal key. */
@RestController
@RequestMapping("/api/v1/avalanche")
@ConditionalOnProperty(name = "avalanche.tx.enabled", havingValue = "true")
public class AvalancheController {

    private final EscrowQueryService queries;
    private final EscrowActionService actions;

    public AvalancheController(EscrowQueryService queries, EscrowActionService actions) {
        this.queries = queries;
        this.actions = actions;
    }

    @GetMapping("/config")
    public ConfigResponse config() {
        return queries.config();
    }

    @GetMapping("/addresses/validate")
    public AddressValidation validate(@RequestParam("address") String address) {
        return queries.validateAddress(address);
    }

    @PostMapping("/wallet-signatures/verify")
    public WalletSignatureResponse verifyWalletSignature(@RequestBody WalletSignatureRequest request) {
        return queries.verifyWalletSignature(request);
    }

    @GetMapping("/assets/balance")
    public BalanceResponse balance(@RequestParam("address") String address) {
        return queries.balance(address);
    }

    @PostMapping("/assets/faucet")
    public FaucetResponse faucet(@RequestBody FaucetRequest request) {
        return actions.faucet(request);
    }

    /** Read-only, no signer: on-chain evidence of a user's own token transfer (payout rail). Not a fiat payout confirmation. */
    @GetMapping("/token-transfers/{txHash}")
    public TokenTransferResponse tokenTransfer(@PathVariable("txHash") String txHash, @RequestParam("from") String from,
                                               @RequestParam("to") String to, @RequestParam("amount") String amount) {
        return queries.verifyTokenTransfer(txHash, from, to, amount);
    }

    /** P3-C4: which issuer controls block this escrow; read-only, never a recovery path (O-6). */
    @GetMapping("/escrows/{escrowId}/alerts")
    public IssuerAlertsResponse alerts(@PathVariable("escrowId") String escrowId) {
        return queries.alerts(escrowId);
    }

    @GetMapping("/escrows/{escrowId}")
    public EscrowResponse escrow(@PathVariable("escrowId") String escrowId) {
        return queries.escrow(escrowId);
    }

    @PostMapping("/escrows/{escrowId}/actions/{action}/prepare")
    public PreparedActionResponse prepare(@PathVariable("escrowId") String escrowId, @PathVariable("action") String action,
                                          @RequestBody ActionRequest request) {
        return actions.prepare(escrowId, action, request);
    }

    @PostMapping("/escrows/{escrowId}/actions/{action}/execute")
    public TransactionResponse execute(@PathVariable("escrowId") String escrowId, @PathVariable("action") String action,
                                       @RequestBody ActionRequest request) {
        return actions.execute(escrowId, action, request);
    }

    @GetMapping("/transactions/{operationKey}")
    public TransactionResponse transaction(@PathVariable("operationKey") String operationKey) {
        return queries.transaction(operationKey);
    }

    @GetMapping("/transactions/by-hash/{txHash}")
    public HashStatusResponse byHash(@PathVariable("txHash") String txHash) {
        return queries.statusByHash(txHash);
    }
}
