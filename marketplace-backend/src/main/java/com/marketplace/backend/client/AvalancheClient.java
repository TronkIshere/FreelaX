package com.marketplace.backend.client;

import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.dto.response.avalanche.GatewayBalanceResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayConfigResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayEscrowResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayHashStatusResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayIssuerAlertsResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayPreparedActionResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayTokenTransferResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayTransactionResponse;
import com.marketplace.backend.exception.AvalancheGatewayException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class AvalancheClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String BASE = "/api/v1/avalanche";

    private final RestTemplate restTemplate;
    private final AvalancheGatewayProperties properties;

    public AvalancheClient(RestTemplateBuilder builder, AvalancheGatewayProperties properties) {
        this.properties = properties;
        this.restTemplate = builder.requestFactory(() -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.getConnectTimeoutMs());
            factory.setReadTimeout(properties.getReadTimeoutMs());
            return factory;
        }).build();
    }

    public GatewayConfigResponse getConfig() {
        return get("config", "/config", GatewayConfigResponse.class).orElseThrow(() -> notFound("config"));
    }

    public GatewayBalanceResponse getBalance(String address) {
        return get("balance", "/assets/balance?address=" + address, GatewayBalanceResponse.class).orElseThrow(() -> notFound("balance"));
    }

    public boolean validateAddress(String address) {
        Map<?, ?> body = get("validate-address", "/addresses/validate?address=" + address, Map.class).orElseThrow(() -> notFound("validate"));
        return Boolean.TRUE.equals(body.get("valid"));
    }

    public Optional<GatewayEscrowResponse> findEscrow(String escrowId) {
        return get("get-escrow", "/escrows/" + escrowId, GatewayEscrowResponse.class);
    }

    public GatewayPreparedActionResponse prepare(String escrowId, String action, Map<String, Object> body) {
        return post("prepare", "/escrows/" + escrowId + "/actions/" + action + "/prepare", body, GatewayPreparedActionResponse.class);
    }

    public GatewayTransactionResponse execute(String escrowId, String action, Map<String, Object> body) {
        return post("execute", "/escrows/" + escrowId + "/actions/" + action + "/execute", body, GatewayTransactionResponse.class);
    }

    public Optional<GatewayTransactionResponse> findTransaction(String operationKey) {
        return get("get-transaction", "/transactions/" + operationKey, GatewayTransactionResponse.class);
    }

    /** P3-C4: issuer controls blocking an escrow (read-only); empty when the escrow does not exist. */
    public Optional<GatewayIssuerAlertsResponse> issuerAlerts(String escrowId) {
        return get("get-issuer-alerts", "/escrows/" + escrowId + "/alerts", GatewayIssuerAlertsResponse.class);
    }

    /** A8: asks the gateway whether {@code address} signed {@code message} (EIP-191 personal_sign). Verification only. */
    public boolean verifyWalletSignature(String address, String message, String signature) {
        Map<?, ?> body = post("verify-wallet-signature", "/wallet-signatures/verify",
                Map.of("address", address, "message", message, "signature", signature), Map.class);
        return Boolean.TRUE.equals(body.get("valid"));
    }

    /** Payout rail: read-only on-chain evidence of a user's own token transfer (never a fiat payout confirmation). */
    public GatewayTokenTransferResponse verifyTokenTransfer(String txHash, String from, String to, String amountBaseUnits) {
        return get("verify-token-transfer", "/token-transfers/" + txHash + "?from=" + from + "&to=" + to + "&amount=" + amountBaseUnits,
                GatewayTokenTransferResponse.class).orElseThrow(() -> notFound("token-transfer"));
    }

    public GatewayHashStatusResponse getStatusByHash(String txHash) {
        return get("get-status-by-hash", "/transactions/by-hash/" + txHash, GatewayHashStatusResponse.class).orElseThrow(() -> notFound("status"));
    }

    private <T> Optional<T> get(String operation, String path, Class<T> type) {
        return Optional.ofNullable(execute(operation, () -> restTemplate.exchange(properties.getBaseUrl() + BASE + path, HttpMethod.GET,
                new HttpEntity<>(null, headers(false)), type).getBody(), true));
    }

    private <T> T post(String operation, String path, Object body, Class<T> type) {
        T result = execute(operation, () -> restTemplate.exchange(properties.getBaseUrl() + BASE + path, HttpMethod.POST,
                new HttpEntity<>(body, headers(true)), type).getBody(), false);
        if (result == null) {
            throw new AvalancheGatewayException(operation + ": empty response", false, null, null);
        }
        return result;
    }

    private HttpHeaders headers(boolean internalKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalKey) {
            headers.set(INTERNAL_API_KEY_HEADER, properties.getInternalApiKey());
        }
        return headers;
    }

    private <T> T execute(String operation, Supplier<T> call, boolean notFoundIsEmpty) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404 && notFoundIsEmpty) {
                return null;
            }
            boolean definitive = status >= 400 && status < 500 && status != 408 && status != 429;
            throw new AvalancheGatewayException(operation + ": HTTP " + status, definitive, status, errorCode(e.getResponseBodyAsString()));
        } catch (ResourceAccessException e) {
            throw new AvalancheGatewayException(operation + ": gateway unreachable", false, null, null);
        }
    }

    private static String errorCode(String body) {
        if (body == null) {
            return null;
        }
        int at = body.indexOf("\"code\":\"");
        if (at < 0) {
            return null;
        }
        int start = at + 8;
        int end = body.indexOf('"', start);
        return end < 0 ? null : body.substring(start, end);
    }

    private static AvalancheGatewayException notFound(String operation) {
        return new AvalancheGatewayException(operation + ": not found", true, 404, null);
    }
}
