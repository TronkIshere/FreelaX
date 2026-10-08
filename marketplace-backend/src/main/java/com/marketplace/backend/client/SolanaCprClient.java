package com.marketplace.backend.client;

import com.marketplace.backend.configuration.RequestCorrelation;

import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.request.solana.CreateInvoiceRequest;
import com.marketplace.backend.dto.request.solana.CompleteOfframpRequest;
import com.marketplace.backend.dto.request.solana.MockOnrampPurchaseRequest;
import com.marketplace.backend.dto.request.solana.PayInvoiceRequest;
import com.marketplace.backend.dto.request.solana.PublishRateRequest;
import com.marketplace.backend.dto.request.solana.RequestOfframpRequest;
import com.marketplace.backend.dto.response.solana.MockOnrampPurchaseResult;
import com.marketplace.backend.dto.response.solana.MockOnrampReceiptResult;
import com.marketplace.backend.dto.response.solana.SolanaAccountResult;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaInvoiceResult;
import com.marketplace.backend.dto.response.solana.SolanaEscrowResult;
import com.marketplace.backend.dto.response.solana.SolanaBuildResult;
import com.marketplace.backend.dto.response.solana.SolanaOperationResult;
import com.marketplace.backend.dto.response.solana.SolanaRateResult;
import com.marketplace.backend.dto.response.solana.SolanaTransactionStatusResult;
import com.marketplace.backend.dto.response.solana.SolanaWithdrawalResult;
import com.marketplace.backend.exception.SolanaCprException;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;
import java.util.Map;

@Component
public class SolanaCprClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestTemplate restTemplate;
    private final SolanaCprProperties properties;

    public SolanaCprClient(RestTemplateBuilder restTemplateBuilder, SolanaCprProperties properties) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(properties.getConnectTimeoutMs());
                    factory.setReadTimeout(properties.getReadTimeoutMs());
                    return factory;
                })
                .build();
    }

    public MockOnrampPurchaseResult createMockOnrampPurchase(MockOnrampPurchaseRequest request) {
        String url = properties.getBaseUrl() + "/api/v1/solana/mock-onramp/purchases";
        MockOnrampPurchaseResult body = execute("mock-onramp", () -> restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(request, jsonHeaders()), MockOnrampPurchaseResult.class));

        if (body == null || !StringUtils.hasText(body.getSignature())) {
            throw new SolanaCprException("mock-onramp: response thieu signature", false, null);
        }
        return body;
    }

    public SolanaTransactionStatusResult getTransactionStatus(String signature) {
        String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/solana/transactions/{signature}")
                .queryParam("commitment", properties.getCommitment())
                .buildAndExpand(signature)
                .toUriString();

        SolanaTransactionStatusResult body = execute("get-transaction", () -> restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()), SolanaTransactionStatusResult.class));

        if (body == null) {
            throw new SolanaCprException("get-transaction: empty body", false, null);
        }
        return body;
    }

    public Optional<MockOnrampReceiptResult> findMockOnrampReceipt(String client, String purchaseId) {
        String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/solana/onramp-receipts/{client}/{purchaseId}")
                .queryParam("commitment", properties.getCommitment())
                .buildAndExpand(client, purchaseId)
                .toUriString();

        try {
            SolanaAccountResult<MockOnrampReceiptResult> body = execute("get-onramp-receipt", () -> restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
                    new ParameterizedTypeReference<SolanaAccountResult<MockOnrampReceiptResult>>() { }));
            return accountData(body).filter(receipt -> StringUtils.hasText(receipt.getPurchaseId()));
        } catch (SolanaCprException e) {
            if (e.getHttpStatus() != null && e.getHttpStatus() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public SolanaConfigResult getConfig() {
        String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/solana/config")
                .queryParam("commitment", properties.getCommitment())
                .toUriString();

        SolanaAccountResult<SolanaConfigResult> body = execute("get-config", () -> restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
                new ParameterizedTypeReference<SolanaAccountResult<SolanaConfigResult>>() { }));

        if (body == null || !body.isExists() || body.getData() == null) {
            throw new SolanaCprException("get-config: empty body", false, null);
        }
        return body.getData();
    }

    public SolanaOperationResult publishRate(PublishRateRequest request) {
        return submitOperation("publish-rate", properties.getBaseUrl() + "/api/v1/solana/rates", request);
    }

    public Optional<SolanaRateResult> findRate(String rateId) {
        String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/solana/rates/{rateId}")
                .queryParam("commitment", properties.getCommitment())
                .buildAndExpand(rateId).toUriString();
        SolanaAccountResult<SolanaRateResult> body = execute("get-rate", () -> restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
                new ParameterizedTypeReference<SolanaAccountResult<SolanaRateResult>>() { }));
        return accountData(body);
    }

    public SolanaOperationResult createInvoice(CreateInvoiceRequest request) {
        return submitOperation("create-invoice", properties.getBaseUrl() + "/api/v1/solana/invoices", request);
    }

    public SolanaOperationResult payInvoice(String freelancer, String invoiceId, PayInvoiceRequest request) {
        String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/solana/invoices/{freelancer}/{invoiceId}/pay")
                .buildAndExpand(freelancer, invoiceId).toUriString();
        return submitOperation("pay-invoice", url, request);
    }

    public Optional<SolanaInvoiceResult> findInvoice(String freelancer, String invoiceId) {
        String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/solana/invoices/{freelancer}/{invoiceId}")
                .queryParam("commitment", properties.getCommitment())
                .buildAndExpand(freelancer, invoiceId).toUriString();
        SolanaAccountResult<SolanaInvoiceResult> body = execute("get-invoice", () -> restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
                new ParameterizedTypeReference<SolanaAccountResult<SolanaInvoiceResult>>() { }));
        return accountData(body);
    }

    public Optional<SolanaEscrowResult> findEscrow(String milestoneId) {
        String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/solana/escrows/{milestoneId}")
                .queryParam("commitment", properties.getCommitment())
                .buildAndExpand(milestoneId).toUriString();
        SolanaAccountResult<SolanaEscrowResult> body = execute("get-escrow", () -> restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
                new ParameterizedTypeReference<SolanaAccountResult<SolanaEscrowResult>>() { }));
        return accountData(body);
    }

    public SolanaBuildResult buildEscrowFund(String milestoneId, Object request) {
        return buildEscrow("fund-escrow", "/api/v1/solana/escrows/" + milestoneId + "/fund", request);
    }

    public SolanaBuildResult buildEscrowAction(String milestoneId, String action, Object request) {
        return buildEscrow("escrow-" + action,
                "/api/v1/solana/escrows/" + milestoneId + "/actions/" + action, request);
    }

    public SolanaOperationResult sendEscrowAction(String milestoneId, String action, Object request) {
        return submitOperation("escrow-" + action,
                properties.getBaseUrl() + "/api/v1/solana/escrows/" + milestoneId
                        + "/actions/" + action, request);
    }

    public SolanaBuildResult buildEscrowMutualRefund(String milestoneId, Object request) {
        return buildEscrow("escrow-mutual-refund",
                "/api/v1/solana/escrows/" + milestoneId + "/mutual-refund", request);
    }

    public String submitEscrowSigned(String buildSessionId, String transactionBase64) {
        String url = properties.getBaseUrl() + "/api/v1/solana/transactions/submit";
        Map<String, Object> request = Map.of("buildSessionId", buildSessionId,
                "transactionBase64", transactionBase64);
        record SubmitResult(String signature) { }
        SubmitResult result = execute("submit-escrow", () -> restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(request, jsonHeaders()), SubmitResult.class));
        if (result == null || !StringUtils.hasText(result.signature())) {
            throw new SolanaCprException("submit-escrow: response thieu signature", false, null);
        }
        return result.signature();
    }

    private SolanaBuildResult buildEscrow(String operation, String path, Object request) {
        SolanaBuildResult result = execute(operation, () -> restTemplate.exchange(
                properties.getBaseUrl() + path, HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()), SolanaBuildResult.class));
        if (result == null || !StringUtils.hasText(result.buildSessionId())
                || !StringUtils.hasText(result.transactionBase64())) {
            throw new SolanaCprException(operation + ": response thieu build session", false, null);
        }
        return result;
    }

    public SolanaOperationResult requestOfframp(RequestOfframpRequest request) {
        return submitOperation("request-offramp", properties.getBaseUrl() + "/api/v1/solana/withdrawals", request);
    }

    public Optional<SolanaWithdrawalResult> findWithdrawal(String freelancer, String withdrawalId) {
        String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/solana/withdrawals/{freelancer}/{withdrawalId}")
                .queryParam("commitment", properties.getCommitment())
                .buildAndExpand(freelancer, withdrawalId).toUriString();
        SolanaAccountResult<SolanaWithdrawalResult> body = execute("get-withdrawal", () -> restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
                new ParameterizedTypeReference<SolanaAccountResult<SolanaWithdrawalResult>>() { }));
        return accountData(body);
    }

    public SolanaOperationResult completeOfframp(String freelancer, String withdrawalId,
                                                 CompleteOfframpRequest request) {
        String url = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .path("/api/v1/solana/withdrawals/{freelancer}/{withdrawalId}/complete")
                .buildAndExpand(freelancer, withdrawalId).toUriString();
        return submitOperation("complete-offramp", url, request);
    }

    private SolanaOperationResult submitOperation(String operation, String url, Object request) {
        SolanaOperationResult body = execute(operation, () -> restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(request, jsonHeaders()), SolanaOperationResult.class));
        if (body == null || !StringUtils.hasText(body.getSignature())) {
            throw new SolanaCprException(operation + ": response thieu signature", false, null);
        }
        return body;
    }

    private <T> Optional<T> accountData(SolanaAccountResult<T> account) {
        if (account == null || !account.isExists() || account.getData() == null) {
            return Optional.empty();
        }
        return Optional.of(account.getData());
    }

    private <T> T execute(String operation, ExchangeCall<T> call) {
        try {
            return call.exchange().getBody();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean definitive = status >= 400 && status < 500 && status != 408 && status != 429;
            throw new SolanaCprException(
                    operation + ": HTTP " + status, definitive, status);
        } catch (ResourceAccessException e) {
            throw new SolanaCprException(
                    operation + ": CONNECTION_FAILED", false, null);
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        RequestCorrelation.add(headers);
        if (StringUtils.hasText(properties.getInternalApiKey())) {
            headers.set(INTERNAL_API_KEY_HEADER, properties.getInternalApiKey());
        }
        return headers;
    }

    @FunctionalInterface
    private interface ExchangeCall<T> {
        ResponseEntity<T> exchange();
    }
}
