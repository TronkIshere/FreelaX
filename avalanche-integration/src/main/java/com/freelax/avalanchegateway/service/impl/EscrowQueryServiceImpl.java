package com.freelax.avalanchegateway.service.impl;

import com.freelax.avalanchegateway.applicationException.ApiException;
import com.freelax.avalanchegateway.applicationException.AvalancheErrorDefinitions;
import com.freelax.avalanchegateway.chain.ChainClient;
import com.freelax.avalanchegateway.chain.TxReceipt;
import com.freelax.avalanchegateway.config.TxProperties;
import com.freelax.avalanchegateway.contract.DeploymentHolder;
import com.freelax.avalanchegateway.contract.DeploymentManifest;
import com.freelax.avalanchegateway.contract.EscrowReader;
import com.freelax.avalanchegateway.contract.EscrowState;
import com.freelax.avalanchegateway.contract.IssuerControls;
import com.freelax.avalanchegateway.contract.TokenTransferMatcher;
import com.freelax.avalanchegateway.contract.WalletSignatureVerifier;
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
import com.freelax.avalanchegateway.entity.TxState;
import com.freelax.avalanchegateway.env.AvalancheEnvironment;
import com.freelax.avalanchegateway.mapper.TransactionResponseMapper;
import com.freelax.avalanchegateway.repository.TxRepository;
import com.freelax.avalanchegateway.service.EscrowQueryService;
import com.freelax.avalanchegateway.signer.SignerRegistry;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.freelax.avalanchegateway.service.impl.RequestFields.address;
import static com.freelax.avalanchegateway.service.impl.RequestFields.escrowId;
import static com.freelax.avalanchegateway.service.impl.RequestFields.txHash;

@Service
@ConditionalOnProperty(name = "avalanche.tx.enabled", havingValue = "true")
public class EscrowQueryServiceImpl implements EscrowQueryService {

    private static final String FUJI_EXPLORER = "https://explorer-test.avax.network/c-chain";
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final int TOKEN_DECIMALS = 6;
    private static final String NO_RECEIPT = "NO_RECEIPT";

    private final ChainClient client;
    private final ValidatedEnvironment environment;
    private final DeploymentHolder deployment;
    private final boolean backendSignersAvailable;
    private final TxRepository repository;
    private final TxProperties txProperties;
    private final TransactionResponseMapper mapper;

    public EscrowQueryServiceImpl(ChainClient client, ValidatedEnvironment environment, DeploymentHolder deployment,
                                  ObjectProvider<SignerRegistry> registry, TxRepository repository, TxProperties txProperties,
                                  TransactionResponseMapper mapper) {
        this.client = client;
        this.environment = environment;
        this.deployment = deployment;
        this.backendSignersAvailable = registry.getIfAvailable() != null;
        this.repository = repository;
        this.txProperties = txProperties;
        this.mapper = mapper;
    }

    @Override
    public ConfigResponse config() {
        DeploymentManifest manifest = deployment.require().manifest();
        EscrowReader reader = reader();
        String explorer = environment.environment() == AvalancheEnvironment.AVALANCHE_FUJI ? FUJI_EXPLORER : null;
        return new ConfigResponse(environmentName(), true, environment.chainId(), manifest.escrow(), manifest.token(), manifest.tokenKind(),
                manifest.contractVersion(), reader.paused(), reader.defaultArbiter(), backendSignersAvailable, explorer);
    }

    @Override
    public AddressValidation validateAddress(String address) {
        return new AddressValidation(address, address != null && ADDRESS.matcher(address).matches());
    }

    @Override
    public WalletSignatureResponse verifyWalletSignature(WalletSignatureRequest request) {
        if (request == null) {
            return new WalletSignatureResponse(null, false);
        }
        boolean valid = WalletSignatureVerifier.verifyPersonalSign(request.address(), request.message(), request.signature());
        return new WalletSignatureResponse(request.address(), valid);
    }

    @Override
    public BalanceResponse balance(String rawAddress) {
        String owner = address(rawAddress);
        String token = deployment.require().manifest().token();
        BigInteger balance = reader().tokenBalance(token, owner);
        return new BalanceResponse(environmentName(), true, owner, token, balance.toString(), TOKEN_DECIMALS);
    }

    @Override
    public EscrowResponse escrow(String rawEscrowId) {
        String id = escrowId(rawEscrowId);
        EscrowState s = existingEscrow(id);
        return new EscrowResponse(environmentName(), true, environment.chainId(), id, s.statusName().toUpperCase(), s.client(), s.freelancer(),
                s.arbiter(), s.amount().toString(), s.maxRevisions(), s.revisionsUsed(), s.extensionUsed(), s.reviewWindowSeconds(),
                s.submissionCount(), s.authNonce(), s.deliveryDueAt(), s.originalDeliveryDueAt(), s.requestedDeliveryDueAt(), s.reviewDueAt(),
                s.submissionHash(), s.resolutionHash(), s.disputedBy(), s.disputedAt());
    }

    @Override
    public IssuerAlertsResponse alerts(String rawEscrowId) {
        String id = escrowId(rawEscrowId);
        EscrowState state = existingEscrow(id);
        DeploymentManifest manifest = deployment.require().manifest();
        List<IssuerControls.Alert> alerts = new IssuerControls(client, manifest.token(), manifest.tokenKind()).assess(state, manifest.escrow());
        return new IssuerAlertsResponse(environmentName(), true, environment.chainId(), id, state.statusName().toUpperCase(), alerts,
                IssuerControls.NOTE);
    }

    @Override
    public TransactionResponse transaction(String operationKey) {
        return repository.find(environmentName(), environment.chainId(), operationKey)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ApiException(AvalancheErrorDefinitions.TRANSACTION_NOT_FOUND));
    }

    @Override
    public HashStatusResponse statusByHash(String rawTxHash) {
        String hash = txHash(rawTxHash);
        Optional<TxReceipt> receipt = client.receipt(hash);
        TxObservation observed = receipt.isPresent() ? observeManifestReceipt(receipt.get()) : new TxObservation(pendingOrUnknown(hash), null);
        return new HashStatusResponse(environmentName(), true, environment.chainId(), hash.toLowerCase(Locale.ROOT), observed.state(),
                observed.block());
    }

    @Override
    public TokenTransferResponse verifyTokenTransfer(String rawTxHash, String from, String to, String amount) {
        txHash(rawTxHash);
        String sender = address(from);
        String recipient = address(to);
        BigInteger value = positiveAmount(amount);
        if (sender.equals(recipient)) {
            throw new ApiException(AvalancheErrorDefinitions.INVALID_ADDRESS, "from and to must differ");
        }
        String token = deployment.require().manifest().token().toLowerCase(Locale.ROOT);
        String hash = rawTxHash.toLowerCase(Locale.ROOT);
        TransferObservation observed = observeTransfer(hash, token, sender, recipient, value);
        return new TokenTransferResponse(environmentName(), true, environment.chainId(), hash, token, sender, recipient, value.toString(),
                observed.state(), observed.match(), observed.block());
    }

    private TxObservation observeManifestReceipt(TxReceipt receipt) {
        if (!isManifestTarget(receipt.to())) {
            return new TxObservation(TxState.UNKNOWN.name(), null);
        }
        long block = receipt.blockNumber();
        return new TxObservation(receipt.success() ? minedState(block) : TxState.REVERTED.name(), block);
    }

    private TransferObservation observeTransfer(String hash, String token, String sender, String recipient, BigInteger value) {
        Optional<TxReceipt> found = client.receipt(hash);
        if (found.isEmpty()) {
            return new TransferObservation(pendingOrUnknown(hash), NO_RECEIPT, null);
        }
        TxReceipt receipt = found.get();
        String notFound = TokenTransferMatcher.Outcome.TRANSFER_NOT_FOUND.name();
        if (receipt.to() == null || !receipt.to().equalsIgnoreCase(token)) {
            return new TransferObservation(TxState.UNKNOWN.name(), notFound, null);
        }
        long block = receipt.blockNumber();
        if (!receipt.success()) {
            return new TransferObservation(TxState.REVERTED.name(), notFound, block);
        }
        String match = TokenTransferMatcher.match(client.receiptLogs(hash).orElse(List.of()), token, sender, recipient, value).name();
        return new TransferObservation(minedState(block), match, block);
    }

    /** A successful receipt is FINALIZED once the environment's finality rule holds, otherwise CONFIRMED. */
    private String minedState(long block) {
        boolean finalized = environment.environment() == AvalancheEnvironment.LOCAL_SIMULATION
                ? client.latestBlockNumber() >= block + txProperties.confirmations()
                : client.finalizedBlockNumber() >= block;
        return (finalized ? TxState.FINALIZED : TxState.CONFIRMED).name();
    }

    private String pendingOrUnknown(String hash) {
        return (client.transactionExists(hash) ? TxState.PENDING : TxState.UNKNOWN).name();
    }

    private boolean isManifestTarget(String to) {
        return to != null && deployment.require().allowedTargets().stream().anyMatch(to::equalsIgnoreCase);
    }

    private static BigInteger positiveAmount(String amount) {
        BigInteger value;
        try {
            value = new BigInteger(amount == null ? "" : amount);
        } catch (NumberFormatException e) {
            throw new ApiException(AvalancheErrorDefinitions.INVALID_AMOUNT);
        }
        if (value.signum() <= 0) {
            throw new ApiException(AvalancheErrorDefinitions.INVALID_AMOUNT);
        }
        return value;
    }

    private EscrowState existingEscrow(String id) {
        EscrowState state = reader().read(id);
        if (state.status() == 0) {
            throw new ApiException(AvalancheErrorDefinitions.ESCROW_NOT_FOUND);
        }
        return state;
    }

    private EscrowReader reader() {
        return new EscrowReader(client, deployment.require().manifest().escrow());
    }

    private String environmentName() {
        return environment.environment().name();
    }

    private record TxObservation(String state, Long block) {
    }

    private record TransferObservation(String state, String match, Long block) {
    }
}
