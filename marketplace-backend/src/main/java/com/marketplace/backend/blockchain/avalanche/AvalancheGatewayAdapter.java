package com.marketplace.backend.blockchain.avalanche;

import com.marketplace.backend.blockchain.AssetReference;
import com.marketplace.backend.blockchain.BlockchainAddress;
import com.marketplace.backend.blockchain.BlockchainGateway;
import com.marketplace.backend.blockchain.BlockchainNetwork;
import com.marketplace.backend.blockchain.ChainConfig;
import com.marketplace.backend.blockchain.EnvironmentInfo;
import com.marketplace.backend.blockchain.EscrowActionRequest;
import com.marketplace.backend.blockchain.EscrowReference;
import com.marketplace.backend.blockchain.EscrowSnapshot;
import com.marketplace.backend.blockchain.NeutralEscrowStatus;
import com.marketplace.backend.blockchain.PreparedAction;
import com.marketplace.backend.blockchain.PreparedTransaction;
import com.marketplace.backend.blockchain.TransactionReference;
import com.marketplace.backend.blockchain.TransactionResult;
import com.marketplace.backend.blockchain.TransactionStatus;
import com.marketplace.backend.client.AvalancheClient;
import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.dto.response.avalanche.AvalancheResults;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class AvalancheGatewayAdapter implements BlockchainGateway {
    private static final String EVM_CALLDATA = "EVM_CALLDATA";

    private final AvalancheClient avalanche;
    private final AvalancheGatewayProperties properties;

    @Override
    public BlockchainNetwork network() {
        return new BlockchainNetwork(properties.getNetwork());
    }

    @Override
    public ChainConfig config() {
        AvalancheResults.Config config = avalanche.getConfig();
        return new ChainConfig(StringUtils.hasText(config.token()) ? new AssetReference(config.token()) : null, config.paused());
    }

    @Override
    public boolean onrampReceiptExists(BlockchainAddress client, String purchaseId) {
        throw new UnsupportedOperationException("the simulated on-ramp receipt does not exist on network " + network().id());
    }

    @Override
    public EnvironmentInfo environmentInfo() {
        AvalancheResults.Config config = avalanche.getConfig();
        return new EnvironmentInfo(config.environment(), config.chainId(), config.isSimulated(), config.explorerBaseUrl(),
                config.backendSignersAvailable());
    }

    @Override
    public boolean validateAddress(BlockchainAddress address) {
        return avalanche.validateAddress(address.value());
    }

    @Override
    public String assetBalance(BlockchainAddress owner) {
        return avalanche.getBalance(owner.value()).balanceBaseUnits();
    }

    @Override
    public PreparedAction prepareAction(EscrowActionRequest request) {
        AvalancheResults.Prepared prepared = avalanche.prepare(request.escrowId(), request.action(), body(request));
        List<PreparedTransaction> transactions = prepared.transactions().stream()
                .map(tx -> new PreparedTransaction(EVM_CALLDATA, tx.purpose(), tx.to(), tx.data(), tx.value(), prepared.chainId())).toList();
        return new PreparedAction(prepared.action(), prepared.escrowId(),
                new EnvironmentInfo(prepared.environment(), prepared.chainId(), prepared.isSimulated(), null, true),
                transactions, prepared.authorizationDeadline(), prepared.typedData(), prepared.typedDataDigest());
    }

    @Override
    public TransactionResult executeAction(EscrowActionRequest request) {
        return result(avalanche.execute(request.escrowId(), request.action(), body(request)));
    }

    @Override
    public EscrowSnapshot escrowSnapshot(EscrowReference escrow) {
        return avalanche.findEscrow(escrow.id()).map(e -> new EscrowSnapshot(new EscrowReference(e.escrowId()),
                null, NeutralEscrowStatus.parse(e.status()), e.client(), e.freelancer(), e.arbiter(), e.amountBaseUnits(), e.maxRevisions(),
                e.revisionsUsed(), e.submissionCount(), e.deliveryDueAt(), e.reviewDueAt(), e.submissionHash(), e.resolutionHash()))
                .orElse(null);
    }

    @Override
    public TransactionResult transactionStatus(TransactionReference transaction) {
        String id = transaction.id();
        if (id != null && id.matches("0x[0-9a-fA-F]{64}")) {
            AvalancheResults.HashStatus status = avalanche.getStatusByHash(id);
            return new TransactionResult(transaction, TransactionStatus.valueOf(status.state()), status.txHash(), status.blockNumber(), null);
        }
        return avalanche.findTransaction(id).map(AvalancheGatewayAdapter::result)
                .orElse(new TransactionResult(transaction, TransactionStatus.UNKNOWN, null, null, "NOT_FOUND"));
    }

    private static TransactionResult result(AvalancheResults.Transaction tx) {
        return new TransactionResult(new TransactionReference(null, tx.operationKey()), TransactionStatus.valueOf(tx.state()), tx.txHash(),
                tx.blockNumber(), tx.errorClass());
    }

    private static Map<String, Object> body(EscrowActionRequest request) {
        Map<String, Object> body = new HashMap<>();
        if (request.parameters() != null) {
            body.putAll(request.parameters());
        }
        if (request.operationKey() != null) {
            body.put("operationKey", request.operationKey());
        }
        return body;
    }
}
