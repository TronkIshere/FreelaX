package com.freelax.avalanchegateway.mapper;

import com.freelax.avalanchegateway.dto.response.TransactionResponse;
import com.freelax.avalanchegateway.entity.TxRecord;
import com.freelax.avalanchegateway.startup.ValidatedEnvironment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "avalanche.tx.enabled", havingValue = "true")
public class TransactionResponseMapper {

    private final ValidatedEnvironment environment;

    public TransactionResponseMapper(ValidatedEnvironment environment) {
        this.environment = environment;
    }

    public TransactionResponse toResponse(TxRecord record) {
        return new TransactionResponse(environment.environment().name(), true, environment.chainId(), record.operationKey(),
                record.state().name(), record.txHash(), record.nonce(), record.blockNumber(), record.lastErrorClass(),
                record.signerRole());
    }
}
