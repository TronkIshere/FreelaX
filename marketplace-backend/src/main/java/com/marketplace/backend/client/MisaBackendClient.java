package com.marketplace.backend.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.dto.response.misa.MisaCertificateStatusResult;
import com.marketplace.backend.dto.response.misa.MisaPayoutTransactionResult;
import com.marketplace.backend.dto.response.misa.MisaCertificateRecoveryResult;
import org.springframework.web.client.RestClientException;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class MisaBackendClient {

    private static final Locale VI_LOCALE = Locale.forLanguageTag("vi-VN");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public MisaBackendClient(RestTemplateBuilder restTemplateBuilder,
                             ObjectMapper objectMapper,
                             @Value("${http-client.connect-timeout-ms:3000}") int connectTimeoutMs,
                             @Value("${http-client.read-timeout-ms:10000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(connectTimeoutMs);
                    factory.setReadTimeout(readTimeoutMs);
                    return factory;
                })
                .build();
    }

    @Value("${misa-backend.base-url}")
    private String baseUrl;

    @Value("${misa-backend.platform-account-email}")
    private String platformAccountEmail;

    @Value("${misa-backend.platform-account-password}")
    private String platformAccountPassword;

    private volatile String cachedAccessToken;
    private volatile Instant cachedTokenExpiresAt;

    public UUID registerTaxpayerForExternal(UUID freelancerId, String fullName, String taxCode,
                                            String identityNumber, String nationality, String address) {
        Map<String, Object> body = new HashMap<>();
        body.put("externalId", freelancerId.toString());
        body.put("fullName", fullName);
        body.put("taxCode", taxCode);
        body.put("identityNumber", identityNumber);
        body.put("nationality", nationality);
        body.put("address", address);

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

    public MisaPayoutTransactionResult recordPayoutTransaction(UUID taxpayerId, UUID payoutReference,
                                                               BigDecimal amountUsd, BigDecimal usdToVndRate,
                                                               String transactionHash, String blockchain) {
        Map<String, Object> body = new HashMap<>();
        body.put("platformPayoutId", payoutReference.toString());
        body.put("transactionHash", transactionHash);
        body.put("blockchain", blockchain);
        body.put("sourceCurrency", "USD");
        body.put("sourceAmount", amountUsd);
        body.put("exchangeRatePair", "USD/VND");
        body.put("exchangeRate", usdToVndRate);
        // MISA's field name is amountUsdc, but the agreed tax base is gross Job USD.
        body.put("amountUsdc", amountUsd);
        body.put("paymentDate", LocalDate.now());
        body.put("description", "Thanh toán thù lao công việc " + payoutReference
                + ". Tỷ giá quy đổi: " + formatRate(usdToVndRate) + " VND/USD");

        ResponseEntity<ResponseAPI<MisaPayoutTransactionResult>> response = restTemplate.exchange(
                baseUrl + "/api/v1/taxpayers/" + taxpayerId + "/payouts",
                HttpMethod.POST, new HttpEntity<>(body, authorizedJsonHeaders()),
                new ParameterizedTypeReference<ResponseAPI<MisaPayoutTransactionResult>>() {});

        MisaPayoutTransactionResult payout = response.getBody() == null ? null : response.getBody().getData();
        if (payout == null || payout.getId() == null) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, "record-payout: missing data.id");
        }
        return payout;
    }

    public MisaCertificateStatusResult createWithholdingCertificate(UUID payoutTransactionId) {
        return createWithholdingCertificate(payoutTransactionId, "payout-" + payoutTransactionId);
    }

    public MisaCertificateStatusResult createWithholdingCertificate(UUID payoutTransactionId, String stableKey) {
        Map<String, Object> body = Map.of("payoutTransactionId", payoutTransactionId.toString(), "idempotencyKey", stableKey);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/v1/withholding-certificates",
                HttpMethod.POST, new HttpEntity<>(body, authorizedJsonHeaders()),
                JsonNode.class);

        MisaCertificateStatusResult result = toCertificateStatus(response.getBody(), "create-certificate");
        if (result.getId() == null) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, "create-certificate: missing id");
        }
        return result;
    }

    public MisaCertificateRecoveryResult findCertificateByPlatformPayout(UUID platformPayoutId) {
        // Authenticate before the lookup try block: auth 404 is not certificate absence.
        HttpHeaders headers = authorizedJsonHeaders();
        try {
            ResponseEntity<ResponseAPI<MisaCertificateRecoveryResult>> response = restTemplate.exchange(
                    baseUrl + "/api/v1/withholding-certificates/by-platform-payout/" + platformPayoutId,
                    HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ResponseAPI<MisaCertificateRecoveryResult>>() {});
            if (response.getBody() == null || !java.util.Objects.equals(response.getBody().getCode(), 200)
                    || response.getBody().getData() == null) {
                throw new RestClientException("Invalid certificate lookup response");
            }
            return response.getBody().getData();
        } catch (HttpClientErrorException.NotFound ex) {
            // Only the MISA certificate-not-found code proves absence, not an unknown route.
            try {
                JsonNode error = objectMapper.readTree(ex.getResponseBodyAsString());
                if (error.path("status").asInt() == 3004) return null;
            } catch (JsonProcessingException ignored) { }
            throw ex;
        }
    }

    public MisaCertificateStatusResult getCertificateStatus(UUID certificateId) {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/v1/withholding-certificates/" + certificateId + "/status",
                HttpMethod.GET, new HttpEntity<>(authorizedJsonHeaders()),
                JsonNode.class);
        return toCertificateStatus(response.getBody(), "get-certificate-status");
    }

    public MisaCertificateStatusResult issueCertificate(UUID certificateId, String digitalCertificateSerial,
                                                        String signatureMode) {
        Map<String, Object> body = new HashMap<>();
        body.put("digitalCertificateSerial", digitalCertificateSerial);
        body.put("signatureMode", signatureMode);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/v1/withholding-certificates/" + certificateId + "/issue",
                HttpMethod.POST, new HttpEntity<>(body, authorizedJsonHeaders()),
                JsonNode.class);
        return toCertificateStatus(response.getBody(), "issue-certificate");
    }

    public MisaCertificateStatusResult submitCertificate(UUID certificateId, String submissionMode, String idempotencyKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("submissionMode", submissionMode);
        body.put("idempotencyKey", idempotencyKey);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/api/v1/withholding-certificates/" + certificateId + "/submit",
                HttpMethod.POST, new HttpEntity<>(body, authorizedJsonHeaders()),
                JsonNode.class);
        return toCertificateStatus(response.getBody(), "submit-certificate");
    }

    private MisaCertificateStatusResult toCertificateStatus(JsonNode body, String operation) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            return new MisaCertificateStatusResult();
        }
        JsonNode node = body.has("data") && body.get("data").isObject() ? body.get("data") : body;
        if (node.isTextual()) {
            MisaCertificateStatusResult result = new MisaCertificateStatusResult();
            result.setStatus(node.asText());
            return result;
        }
        MisaCertificateStatusResult result;
        try {
            result = objectMapper.treeToValue(node, MisaCertificateStatusResult.class);
        } catch (JsonProcessingException e) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, operation + ": khong doc duoc response");
        }

        JsonNode form = node.path("form");
        if (result.getSymbol() == null && form.hasNonNull("symbol")) {
            result.setSymbol(form.get("symbol").asText());
        }
        if (result.getCertificateNumber() == null && form.hasNonNull("number")) {
            result.setCertificateNumber(form.get("number").asText());
        }

        JsonNode income = node.path("income");
        if (result.getTaxWithheld() == null && income.hasNonNull("taxWithheld")) {
            result.setTaxWithheld(income.get("taxWithheld").decimalValue());
        }
        if (result.getTaxableIncome() == null && income.hasNonNull("taxableIncome")) {
            result.setTaxableIncome(income.get("taxableIncome").decimalValue());
        }
        return result;
    }

    public byte[] getCertificatePdf(UUID certificateId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getOrRefreshAccessToken());

        ResponseEntity<byte[]> response = restTemplate.exchange(
                baseUrl + "/api/v1/withholding-certificates/" + certificateId + "/pdf",
                HttpMethod.GET, new HttpEntity<>(headers),
                byte[].class);

        if (response.getBody() == null) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, "get-certificate-pdf: empty body");
        }
        return response.getBody();
    }

    private String formatRate(BigDecimal rate) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(VI_LOCALE));
        return format.format(rate);
    }

    public byte[] getCertificateXml(UUID certificateId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getOrRefreshAccessToken());

        ResponseEntity<byte[]> response = restTemplate.exchange(
                baseUrl + "/api/v1/withholding-certificates/" + certificateId + "/xml",
                HttpMethod.GET, new HttpEntity<>(headers),
                byte[].class);

        if (response.getBody() == null) {
            throw new ApplicationException(ErrorCode.MISA_BACKEND_CALL_FAILED, "get-certificate-xml: empty body");
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
