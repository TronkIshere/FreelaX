package com.marketplace.backend.client;

import com.marketplace.backend.dto.request.solana.MockOnrampPurchaseRequest;
import com.marketplace.backend.dto.response.solana.MockOnrampPurchaseResult;
import com.marketplace.backend.dto.response.solana.SolanaTransactionStatusResult;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Component
public class SolanaCprClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestTemplate restTemplate;

    public SolanaCprClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${solana-cpr.base-url}")
    private String baseUrl;

    @Value("${solana-cpr.internal-api-key:}")
    private String internalApiKey;

    public MockOnrampPurchaseResult createMockOnrampPurchase(MockOnrampPurchaseRequest request) {
        try {
            ResponseEntity<MockOnrampPurchaseResult> response = restTemplate.exchange(
                    baseUrl + "/api/v1/demo/onramp/purchases",
                    HttpMethod.POST, new HttpEntity<>(request, jsonHeaders()),
                    MockOnrampPurchaseResult.class);

            MockOnrampPurchaseResult body = response.getBody();
            if (body == null || !StringUtils.hasText(body.getSignature())) {
                throw new ApplicationException(ErrorCode.SOLANA_CPR_CALL_FAILED, "mock-onramp: empty body or missing signature");
            }
            return body;
        } catch (RestClientResponseException e) {
            throw new ApplicationException(ErrorCode.SOLANA_CPR_CALL_FAILED,
                    "mock-onramp: HTTP " + e.getStatusCode().value() + " " + e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new ApplicationException(ErrorCode.SOLANA_CPR_CALL_FAILED,
                    "mock-onramp: khong ket noi duoc " + baseUrl);
        }
    }

    public SolanaTransactionStatusResult getTransactionStatus(String signature) {
        try {
            ResponseEntity<SolanaTransactionStatusResult> response = restTemplate.exchange(
                    baseUrl + "/api/v1/transactions/" + signature,
                    HttpMethod.GET, new HttpEntity<>(jsonHeaders()),
                    SolanaTransactionStatusResult.class);

            SolanaTransactionStatusResult body = response.getBody();
            if (body == null) {
                throw new ApplicationException(ErrorCode.SOLANA_CPR_CALL_FAILED, "get-transaction: empty body");
            }
            return body;
        } catch (RestClientResponseException e) {
            throw new ApplicationException(ErrorCode.SOLANA_CPR_CALL_FAILED,
                    "get-transaction: HTTP " + e.getStatusCode().value() + " " + e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new ApplicationException(ErrorCode.SOLANA_CPR_CALL_FAILED,
                    "get-transaction: khong ket noi duoc " + baseUrl);
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(internalApiKey)) {
            headers.set(INTERNAL_API_KEY_HEADER, internalApiKey);
        }
        return headers;
    }
}