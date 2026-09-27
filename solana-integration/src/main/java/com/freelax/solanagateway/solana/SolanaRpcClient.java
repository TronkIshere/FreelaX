package com.freelax.solanagateway.solana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.freelax.solanagateway.api.ApiTypes.Commitment;
import com.freelax.solanagateway.config.SolanaProperties;
import com.freelax.solanagateway.exception.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SolanaRpcClient {

    public record AccountData(String owner, long lamports, byte[] data) {
    }

    public record LatestBlockhash(String blockhash, long lastValidBlockHeight) {
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SolanaProperties properties;
    private final AtomicLong requestIds = new AtomicLong();

    public SolanaRpcClient(RestClient solanaRestClient, ObjectMapper objectMapper,
                           SolanaProperties properties) {
        this.restClient = solanaRestClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public AccountData getAccountInfo(String address, Commitment commitment) {
        ObjectNode config = objectMapper.createObjectNode()
                .put("encoding", "base64")
                .put("commitment", commitment(commitment));
        JsonNode value = call("getAccountInfo", array(address, config)).path("value");
        if (value.isNull() || value.isMissingNode()) {
            return null;
        }
        JsonNode encoded = value.path("data");
        if (!encoded.isArray() || encoded.isEmpty()) {
            throw rpcError("Solana returned account data without base64 payload");
        }
        return new AccountData(value.path("owner").asText(), value.path("lamports").asLong(),
                Base64.getDecoder().decode(encoded.get(0).asText()));
    }

    public JsonNode getTokenAccountBalance(String ata, Commitment commitment) {
        ObjectNode config = objectMapper.createObjectNode().put("commitment", commitment(commitment));
        return call("getTokenAccountBalance", array(ata, config)).path("value");
    }

    public LatestBlockhash getLatestBlockhash(Commitment commitment) {
        ObjectNode config = objectMapper.createObjectNode().put("commitment", commitment(commitment));
        JsonNode value = call("getLatestBlockhash", array(config)).path("value");
        return new LatestBlockhash(value.path("blockhash").asText(),
                value.path("lastValidBlockHeight").asLong());
    }

    public long getBlockHeight(Commitment commitment) {
        ObjectNode config = objectMapper.createObjectNode().put("commitment", commitment(commitment));
        return call("getBlockHeight", array(config)).asLong();
    }

    public long getMinimumBalanceForRentExemption(int dataLength, Commitment commitment) {
        ObjectNode config = objectMapper.createObjectNode().put("commitment", commitment(commitment));
        return call("getMinimumBalanceForRentExemption", array(dataLength, config)).asLong();
    }

    public long getBalance(String address, Commitment commitment) {
        ObjectNode config = objectMapper.createObjectNode().put("commitment", commitment(commitment));
        return call("getBalance", array(address, config)).path("value").asLong();
    }

    public String sendTransaction(String transactionBase64, boolean skipPreflight,
                                  Commitment commitment) {
        ObjectNode config = objectMapper.createObjectNode()
                .put("encoding", "base64")
                .put("skipPreflight", skipPreflight)
                .put("preflightCommitment", commitment(commitment))
                .put("maxRetries", 3);
        return call("sendTransaction", array(transactionBase64, config)).asText();
    }

    public JsonNode signatureStatus(String signature) {
        ArrayNode signatures = objectMapper.createArrayNode().add(signature);
        ObjectNode config = objectMapper.createObjectNode().put("searchTransactionHistory", true);
        JsonNode values = call("getSignatureStatuses", array(signatures, config)).path("value");
        return values.isArray() && !values.isEmpty() ? values.get(0) : null;
    }

    public JsonNode transaction(String signature, Commitment commitment) {
        ObjectNode config = objectMapper.createObjectNode()
                .put("encoding", "json")
                .put("commitment", commitment(commitment))
                .put("maxSupportedTransactionVersion", 0);
        return call("getTransaction", array(signature, config));
    }

    private JsonNode call(String method, ArrayNode params) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", requestIds.incrementAndGet())
                .put("method", method)
                .set("params", params);
        try {
            JsonNode response = restClient.post().body(body).retrieve().body(JsonNode.class);
            if (response == null) {
                throw rpcError("Empty response for " + method);
            }
            if (response.hasNonNull("error")) {
                JsonNode error = response.get("error");
                String message = error.path("message").asText("RPC request failed");
                throw new GatewayException(HttpStatus.BAD_GATEWAY, "SOLANA_RPC_ERROR", message);
            }
            if (!response.has("result")) {
                throw rpcError("Missing result for " + method);
            }
            return response.get("result");
        } catch (GatewayException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "SOLANA_RPC_UNAVAILABLE",
                    exception.getMessage());
        }
    }

    private ArrayNode array(Object... values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (Object value : values) {
            array.addPOJO(value);
        }
        return array;
    }

    private String commitment(Commitment requested) {
        return requested == null ? properties.commitment() : requested.name();
    }

    private GatewayException rpcError(String message) {
        return new GatewayException(HttpStatus.BAD_GATEWAY, "INVALID_SOLANA_RPC_RESPONSE", message);
    }
}
