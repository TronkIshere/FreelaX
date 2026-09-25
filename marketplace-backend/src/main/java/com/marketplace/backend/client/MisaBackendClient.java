package com.marketplace.backend.client;

import com.marketplace.backend.dto.response.misa.MisaCertificateResult;
import com.marketplace.backend.dto.response.misa.MisaPayoutTransactionResult;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Component
public class MisaBackendClient {

    private final RestTemplate restTemplate;

    public MisaBackendClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${misa-backend.base-url}")
    private String baseUrl;

    @Value("${misa-backend.provider-client-id}")
    private String clientId;

    @Value("${misa-backend.provider-client-secret}")
    private String clientSecret;

    private volatile String cachedAccessToken;
    private volatile Instant cachedTokenExpiresAt;

    public MisaPayoutTransactionResult recordPayoutTransaction(UUID taxpayerId, UUID payoutReleaseId, BigDecimal budgetUsd) {
        Map<String, Object> body = Map.of(
                "platformPayoutId", payoutReleaseId.toString(),
                "transactionHash", "paypal:" + payoutReleaseId,
                "blockchain", "paypal",
                "amountUsdc", budgetUsd,
                "exchangeRate", BigDecimal.ONE,
                "paymentDate", LocalDate.now(),
                "description", "Payout PayPal cho job marketplace, payoutReleaseId=" + payoutReleaseId
        );

        ResponseEntity<MisaPayoutTransactionResult> response = restTemplate.exchange(
                baseUrl + "/api/v1/taxpayers/" + taxpayerId + "/payouts",
                HttpMethod.POST, new HttpEntity<>(body, authorizedJsonHeaders()),
                MisaPayoutTransactionResult.class);

        if (response.getBody() == null) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, "record-payout: empty body");
        }
        return response.getBody();
    }

    public MisaCertificateResult createWithholdingCertificate(UUID payoutTransactionId) {
        Map<String, Object> body = Map.of("payoutTransactionId", payoutTransactionId.toString());

        ResponseEntity<MisaCertificateResult> response = restTemplate.exchange(
                baseUrl + "/api/v1/withholding-certificates",
                HttpMethod.POST, new HttpEntity<>(body, authorizedJsonHeaders()),
                MisaCertificateResult.class);

        if (response.getBody() == null) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, "create-certificate: empty body");
        }
        return response.getBody();
    }

    private HttpHeaders authorizedJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getOrRefreshAccessToken());
        return headers;
    }

    private synchronized String getOrRefreshAccessToken() {
        if (cachedAccessToken != null && cachedTokenExpiresAt != null && Instant.now().isBefore(cachedTokenExpiresAt)) {
            return cachedAccessToken;
        }

        Map<String, Object> tokenRequest = Map.of(
                "clientId", clientId, "clientSecret", clientSecret, "grantType", "client_credentials");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
                baseUrl + "/api/v1/auth/token", new HttpEntity<>(tokenRequest, headers), Map.class);

        if (response == null || response.get("accessToken") == null) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, "token: empty response");
        }

        cachedAccessToken = (String) response.get("accessToken");
        Object expiresIn = response.getOrDefault("expiresInSeconds", 300);
        cachedTokenExpiresAt = Instant.now().plusSeconds(((Number) expiresIn).longValue() - 30);
        return cachedAccessToken;
    }
}