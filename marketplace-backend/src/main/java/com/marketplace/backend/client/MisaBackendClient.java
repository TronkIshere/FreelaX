package com.marketplace.backend.client;

import com.marketplace.backend.dto.request.auth.RegisterRequest;
import com.marketplace.backend.dto.response.misa.MisaCertificateResult;
import com.marketplace.backend.dto.response.misa.MisaPayoutTransactionResult;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
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

    @Value("${misa-backend.platform-account-email}")
    private String platformAccountEmail;

    @Value("${misa-backend.platform-account-password}")
    private String platformAccountPassword;

    private volatile String cachedAccessToken;
    private volatile Instant cachedTokenExpiresAt;

    public UUID registerTaxpayerForExternal(UUID freelancerId, RegisterRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("externalId", freelancerId.toString());
        body.put("fullName", request.getDisplayName());
        body.put("taxCode", request.getTaxCode());
        body.put("identityNumber", request.getIdentityNumber());
        body.put("nationality", request.getNationality());
        body.put("address", request.getTaxAddress());

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/api/v1/taxpayers/external",
                HttpMethod.POST, new HttpEntity<>(body, authorizedJsonHeaders()),
                Map.class);

        if (response.getBody() == null || response.getBody().get("data") == null) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, "register-taxpayer-external: empty body");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        Object id = data.get("id");
        if (id == null) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, "register-taxpayer-external: missing id");
        }

        return UUID.fromString(id.toString());
    }

    public MisaPayoutTransactionResult recordPayoutTransaction(UUID taxpayerId, UUID payoutReleaseId,
                                                               BigDecimal amountUsdc, BigDecimal exchangeRate) {
        Map<String, Object> body = Map.of(
                "platformPayoutId", payoutReleaseId.toString(),
                "transactionHash", "paypal:" + payoutReleaseId,
                "blockchain", "paypal",
                "amountUsdc", amountUsdc,
                "exchangeRate", exchangeRate,
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

        Map<String, Object> body = Map.of("email", platformAccountEmail, "password", platformAccountPassword);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> response;
        try {
            response = restTemplate.postForObject(
                    baseUrl + "/api/v1/auth/sign-in", new HttpEntity<>(body, headers), Map.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED,
                    "sign-in: sai email/password cua tai khoan platform -- kiem tra lai " +
                            "misa-backend.platform-account-email/password co khop voi seed ben misa-backend khong");
        }

        if (response == null || response.get("data") == null) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, "sign-in: empty response");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        Object accessToken = data.get("accessToken");
        if (accessToken == null) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, "sign-in: missing accessToken");
        }

        cachedAccessToken = accessToken.toString();
        cachedTokenExpiresAt = extractExpiry(cachedAccessToken);
        return cachedAccessToken;
    }

    private Instant extractExpiry(String jwt) {
        try {
            Date exp = SignedJWT.parse(jwt).getJWTClaimsSet().getExpirationTime();
            if (exp == null) {
                return Instant.now().plusSeconds(300);
            }
            return exp.toInstant().minusSeconds(30);
        } catch (ParseException e) {
            return Instant.now().plusSeconds(300);
        }
    }
}