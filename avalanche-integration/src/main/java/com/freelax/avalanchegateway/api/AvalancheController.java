package com.freelax.avalanchegateway.api;

import com.freelax.avalanchegateway.api.Dtos.ActionRequest;
import com.freelax.avalanchegateway.api.Dtos.AddressValidation;
import com.freelax.avalanchegateway.api.Dtos.BalanceResponse;
import com.freelax.avalanchegateway.api.Dtos.ConfigResponse;
import com.freelax.avalanchegateway.api.Dtos.EscrowResponse;
import com.freelax.avalanchegateway.api.Dtos.FaucetRequest;
import com.freelax.avalanchegateway.api.Dtos.FaucetResponse;
import com.freelax.avalanchegateway.api.Dtos.HashStatusResponse;
import com.freelax.avalanchegateway.api.Dtos.PreparedActionResponse;
import com.freelax.avalanchegateway.api.Dtos.TransactionResponse;
import com.freelax.avalanchegateway.api.Dtos.WalletSignatureRequest;
import com.freelax.avalanchegateway.api.Dtos.WalletSignatureResponse;
import com.freelax.avalanchegateway.contract.DeploymentManifest;
import com.freelax.avalanchegateway.contract.EscrowState;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.tx.TxRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;

/** The gateway API. Every response names its environment and says the money is simulated; POSTs need the internal key. */
@RestController
@RequestMapping("/api/v1/avalanche")
@ConditionalOnProperty(name = "avalanche.tx.enabled", havingValue = "true")
public class AvalancheController {

    private static final String FUJI_EXPLORER = "https://explorer-test.avax.network/c-chain";

    private final EscrowActionService service;

    public AvalancheController(EscrowActionService service) {
        this.service = service;
    }

    private String envName() {
        return service.environment().environment().name();
    }

    private long chainId() {
        return service.environment().chainId();
    }

    @GetMapping("/config")
    public ConfigResponse config() {
        DeploymentManifest m = service.deployment().manifest();
        var reader = service.reader();
        return new ConfigResponse(envName(), true, chainId(), m.escrow(), m.token(), m.tokenKind(), m.contractVersion(), reader.paused(),
                reader.defaultArbiter(), service.backendSignersAvailable(),
                service.environment().environment() == AvalancheEnvironment.AVALANCHE_FUJI ? FUJI_EXPLORER : null);
    }

    @GetMapping("/addresses/validate")
    public AddressValidation validate(@RequestParam("address") String address) {
        boolean valid = address != null && address.matches("0x[0-9a-fA-F]{40}");
        return new AddressValidation(address, valid);
    }

    /** Wallet linking (A8): verifies that {@code address} signed {@code message} with EIP-191 personal_sign. */
    @PostMapping("/wallet-signatures/verify")
    public WalletSignatureResponse verifyWalletSignature(@RequestBody WalletSignatureRequest request) {
        boolean valid = request != null && com.freelax.avalanchegateway.contract.WalletSignatureVerifier
                .verifyPersonalSign(request.address(), request.message(), request.signature());
        return new WalletSignatureResponse(request == null ? null : request.address(), valid);
    }

    @GetMapping("/assets/balance")
    public BalanceResponse balance(@RequestParam("address") String address) {
        String owner = EscrowActionService.requireAddress(address);
        String token = service.deployment().manifest().token();
        BigInteger balance = service.reader().tokenBalance(token, owner);
        return new BalanceResponse(envName(), true, owner, token, balance.toString(), 6);
    }

    @PostMapping("/assets/faucet")
    public FaucetResponse faucet(@RequestBody FaucetRequest request) {
        TxRecord record = service.faucet(request.address(), request.amount());
        return new FaucetResponse(envName(), true, request.address(), request.amount(), toResponse(record));
    }

    @GetMapping("/escrows/{escrowId}")
    public EscrowResponse escrow(@PathVariable("escrowId") String escrowId) {
        String id = EscrowActionService.requireEscrowId(escrowId);
        EscrowState s = service.reader().read(id);
        if (s.status() == 0) {
            throw new ApiException(404, "ESCROW_NOT_FOUND", "no escrow with this id on this chain");
        }
        return new EscrowResponse(envName(), true, chainId(), id, s.statusName().toUpperCase(), s.client(), s.freelancer(), s.arbiter(),
                s.amount().toString(), s.maxRevisions(), s.revisionsUsed(), s.extensionUsed(), s.reviewWindowSeconds(), s.submissionCount(),
                s.authNonce(), s.deliveryDueAt(), s.originalDeliveryDueAt(), s.requestedDeliveryDueAt(), s.reviewDueAt(), s.submissionHash(),
                s.resolutionHash(), s.disputedBy(), s.disputedAt());
    }

    @PostMapping("/escrows/{escrowId}/actions/{action}/prepare")
    public PreparedActionResponse prepare(@PathVariable("escrowId") String escrowId, @PathVariable("action") String action,
                                          @RequestBody ActionRequest request) {
        return service.prepare(escrowId, action, request);
    }

    @PostMapping("/escrows/{escrowId}/actions/{action}/execute")
    public TransactionResponse execute(@PathVariable("escrowId") String escrowId, @PathVariable("action") String action,
                                       @RequestBody ActionRequest request) {
        return toResponse(service.execute(escrowId, action, request));
    }

    @GetMapping("/transactions/{operationKey}")
    public TransactionResponse transaction(@PathVariable("operationKey") String operationKey) {
        return service.transaction(operationKey).map(this::toResponse)
                .orElseThrow(() -> new ApiException(404, "TRANSACTION_NOT_FOUND", "no transaction with this operation key"));
    }

    @GetMapping("/transactions/by-hash/{txHash}")
    public HashStatusResponse byHash(@PathVariable("txHash") String txHash) {
        return service.statusByHash(txHash);
    }

    private TransactionResponse toResponse(TxRecord r) {
        return new TransactionResponse(envName(), true, chainId(), r.operationKey(), r.state().name(), r.txHash(), r.nonce(), r.blockNumber(),
                r.lastErrorClass(), r.signerRole());
    }
}
