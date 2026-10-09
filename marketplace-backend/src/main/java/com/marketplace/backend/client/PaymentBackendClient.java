package com.marketplace.backend.client;

import com.marketplace.backend.configuration.PaymentBackendProperties;
import com.marketplace.backend.configuration.RequestCorrelation;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.bofa.CheckoutOrderResult;
import com.marketplace.backend.dto.response.bofa.PaymentReleaseResult;
import com.marketplace.backend.dto.request.bofa.CreateReleaseRequest;
import com.marketplace.backend.dto.request.bofa.CreateRefundRequest;
import com.marketplace.backend.dto.response.bofa.PaymentRefundResult;
import com.marketplace.backend.dto.response.partner.PartnerEscrowResult;
import com.marketplace.backend.dto.response.partner.PartnerStatementResult;
import com.marketplace.backend.dto.response.payment.UnifiedUsdOrderResult;
import com.marketplace.backend.dto.response.payment.UnifiedUsdStatementResult;
import com.marketplace.backend.dto.response.payment.UnifiedFiatExitResult;
import com.marketplace.backend.dto.response.payment.UnifiedFiatExitStatementResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentBackendClient {

    RestTemplate restTemplate;
    PaymentBackendProperties properties;

    public CheckoutOrderResult createCheckoutOrder(UUID payerUserId, UUID jobId, BigDecimal amountUsd,
                                                   String payerBankCode, String payerBankAccountNumber,
                                                   String payerBankAccountHolderName) {
        Map<String, Object> body = Map.of(
                "payerUserId", payerUserId,
                "jobId", jobId,
                "amountUsd", amountUsd,
                "payerBankCode", payerBankCode,
                "payerBankAccountNumber", payerBankAccountNumber,
                "payerBankAccountHolderName", payerBankAccountHolderName
        );
        return exchange(
                "/internal/BofA/checkout/orders",
                HttpMethod.POST,
                body,
                new ParameterizedTypeReference<ResponseAPI<CheckoutOrderResult>>() {}
        );
    }

    public UnifiedUsdOrderResult openUnifiedUsdOrder(UUID paymentFlowId, UUID jobId,
            UUID contractId, UUID milestoneId, UUID clientId, BigDecimal grossUsd,
            BigDecimal escrowUsdc, String payerBankCode, String payerBankAccountNumber,
            String payerBankAccountHolderName, String fundKey) {
        return unifiedExchange("/internal/unified-mock/usd-orders", HttpMethod.POST,
                Map.ofEntries(Map.entry("paymentFlowId", paymentFlowId), Map.entry("jobId", jobId),
                        Map.entry("contractId", contractId), Map.entry("milestoneId", milestoneId),
                        Map.entry("clientId", clientId), Map.entry("grossUsd", grossUsd),
                        Map.entry("escrowUsdc", escrowUsdc), Map.entry("payerBankCode", payerBankCode),
                        Map.entry("payerBankAccountNumber", payerBankAccountNumber),
                        Map.entry("payerBankAccountHolderName", payerBankAccountHolderName),
                        Map.entry("fundKey", fundKey)),
                new ParameterizedTypeReference<ResponseAPI<UnifiedUsdOrderResult>>() {});
    }

    public UnifiedUsdOrderResult getUnifiedUsdOrder(UUID paymentFlowId) {
        return unifiedExchange("/internal/unified-mock/usd-orders/" + paymentFlowId,
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ResponseAPI<UnifiedUsdOrderResult>>() {});
    }

    public UnifiedUsdOrderResult submitUnifiedUsdOrder(UUID paymentFlowId, String fundKey) {
        return unifiedExchange("/internal/unified-mock/usd-orders/" + paymentFlowId + "/submit",
                HttpMethod.POST, Map.of("fundKey", fundKey),
                new ParameterizedTypeReference<ResponseAPI<UnifiedUsdOrderResult>>() {});
    }

    public UnifiedUsdStatementResult getUnifiedUsdStatement(UUID paymentFlowId) {
        return unifiedExchange("/internal/unified-mock/usd-orders/" + paymentFlowId + "/statement",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ResponseAPI<UnifiedUsdStatementResult>>() {});
    }

    public UnifiedFiatExitResult requestUnifiedFiatExit(UUID paymentFlowId, UUID jobId,
            UUID contractId, UUID milestoneId, String kind, String idempotencyKey,
            String withdrawalReference, String beneficiary, BigDecimal grossUsdc,
            BigDecimal grossUsd) {
        return unifiedExchange("/internal/unified-mock/fiat-exits", HttpMethod.POST,
                Map.of("paymentFlowId", paymentFlowId, "jobId", jobId,
                        "contractId", contractId, "milestoneId", milestoneId,
                        "kind", kind, "idempotencyKey", idempotencyKey,
                        "withdrawalReference", withdrawalReference, "beneficiary", beneficiary,
                        "grossUsdc", grossUsdc, "grossUsd", grossUsd),
                new ParameterizedTypeReference<ResponseAPI<UnifiedFiatExitResult>>() {});
    }

    public UnifiedFiatExitResult getUnifiedFiatExit(UUID paymentFlowId) {
        return unifiedExchange("/internal/unified-mock/fiat-exits/" + paymentFlowId,
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ResponseAPI<UnifiedFiatExitResult>>() {});
    }

    public UnifiedFiatExitStatementResult getUnifiedFiatExitStatement(UUID paymentFlowId) {
        return unifiedExchange("/internal/unified-mock/fiat-exits/" + paymentFlowId + "/statement",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<ResponseAPI<UnifiedFiatExitStatementResult>>() {});
    }

    private <T> T unifiedExchange(String path, HttpMethod method, Object body,
            ParameterizedTypeReference<ResponseAPI<T>> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());
        RequestCorrelation.add(headers);
        ResponseEntity<ResponseAPI<T>> response = restTemplate.exchange(
                properties.getBaseUrl() + path, method, new HttpEntity<>(body, headers), type);
        if (response.getBody() == null || response.getBody().getCode() != 200
                || response.getBody().getData() == null)
            throw new RestClientException("Invalid unified mock response");
        return response.getBody().getData();
    }

    public CheckoutOrderResult captureCheckoutOrder(UUID checkoutOrderId) {
        return exchange(
                "/internal/BofA/checkout/orders/" + checkoutOrderId + "/capture",
                HttpMethod.POST,
                null,
                new ParameterizedTypeReference<ResponseAPI<CheckoutOrderResult>>() {}
        );
    }

    public CheckoutOrderResult getCheckoutOrder(UUID checkoutOrderId) {
        return exchange(
                "/internal/BofA/checkout/orders/" + checkoutOrderId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ResponseAPI<CheckoutOrderResult>>() {}
        );
    }

    public CheckoutOrderResult findFundingOrder(String providerKey) {
        String path = UriComponentsBuilder.fromPath("/internal/BofA/checkout/orders/by-idempotency-key")
                .queryParam("key", providerKey).build().encode().toUriString();
        try {
            return fundingExchange(path, HttpMethod.GET, null);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }

    public CheckoutOrderResult createFundingOrder(UUID payerUserId, UUID jobId, BigDecimal amountUsd,
                                                  String bankCode, String accountNumber, String holderName,
                                                  String providerKey) {
        return fundingExchange("/internal/BofA/checkout/orders", HttpMethod.POST, Map.of(
                "payerUserId", payerUserId, "jobId", jobId, "amountUsd", amountUsd,
                "payerBankCode", bankCode, "payerBankAccountNumber", accountNumber,
                "payerBankAccountHolderName", holderName, "idempotencyKey", providerKey));
    }

    public CheckoutOrderResult captureFundingOrder(UUID orderId) {
        return fundingExchange("/internal/BofA/checkout/orders/" + orderId + "/capture", HttpMethod.POST, null);
    }

    public CheckoutOrderResult getFundingOrder(UUID orderId) {
        return fundingExchange("/internal/BofA/checkout/orders/" + orderId, HttpMethod.GET, null);
    }

    private CheckoutOrderResult fundingExchange(String path, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());
        RequestCorrelation.add(headers);
        ResponseEntity<ResponseAPI<CheckoutOrderResult>> response = restTemplate.exchange(
                properties.getBaseUrl() + path, method, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<ResponseAPI<CheckoutOrderResult>>() {});
        return response.getBody() != null ? response.getBody().getData() : null;
    }

    public PaymentReleaseResult createRelease(CreateReleaseRequest request) {
        return releaseExchange("/internal/BofA/payout/releases", HttpMethod.POST, request);
    }

    public PaymentRefundResult createRefund(CreateRefundRequest request) {
        return refundExchange("/internal/BofA/refunds", HttpMethod.POST, request);
    }

    public PartnerEscrowResult openPartnerEscrow(UUID milestoneId, UUID contractId, UUID jobId,
            UUID clientId, UUID freelancerId, BigDecimal grossUsd, String fundKey) {
        return partnerEscrowExchange("/internal/partner-mock/escrows", HttpMethod.POST,
                Map.of("milestoneId", milestoneId, "contractId", contractId, "jobId", jobId,
                        "clientId", clientId, "freelancerId", freelancerId,
                        "grossUsd", grossUsd, "fundKey", fundKey));
    }

    public PartnerEscrowResult getPartnerEscrow(UUID milestoneId) {
        return partnerEscrowExchange("/internal/partner-mock/escrows/" + milestoneId,
                HttpMethod.GET, null);
    }

    public PartnerEscrowResult freezePartnerEscrow(UUID milestoneId) {
        return partnerEscrowExchange("/internal/partner-mock/escrows/" + milestoneId + "/freeze",
                HttpMethod.POST, null);
    }

    public PartnerEscrowResult releasePartnerEscrow(UUID milestoneId, String releaseKey,
            String bankCode, String bankAccountNumber, boolean adminResolution) {
        return partnerEscrowExchange("/internal/partner-mock/escrows/" + milestoneId + "/release?adminResolution="
                + adminResolution, HttpMethod.POST, Map.of("releaseKey", releaseKey,
                "bankCode", bankCode, "bankAccountNumber", bankAccountNumber));
    }

    public PartnerEscrowResult refundPartnerEscrow(UUID milestoneId, String refundKey,
            boolean adminResolution) {
        return partnerEscrowExchange("/internal/partner-mock/escrows/" + milestoneId + "/refund?adminResolution="
                + adminResolution, HttpMethod.POST, Map.of("refundKey", refundKey));
    }

    public PartnerStatementResult getPartnerStatement() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());
        RequestCorrelation.add(headers);
        ResponseEntity<ResponseAPI<PartnerStatementResult>> response = restTemplate.exchange(
                properties.getBaseUrl() + "/internal/partner-mock/escrows/statement", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ResponseAPI<PartnerStatementResult>>() {});
        if (response.getBody() == null || response.getBody().getCode() != 200
                || response.getBody().getData() == null) throw new RestClientException("Invalid partner statement");
        return response.getBody().getData();
    }

    private PartnerEscrowResult partnerEscrowExchange(String path, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());
        RequestCorrelation.add(headers);
        ResponseEntity<ResponseAPI<PartnerEscrowResult>> response = restTemplate.exchange(
                properties.getBaseUrl() + path, method, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<ResponseAPI<PartnerEscrowResult>>() {});
        if (response.getBody() == null || response.getBody().getCode() != 200
                || response.getBody().getData() == null) throw new RestClientException("Invalid partner escrow response");
        return response.getBody().getData();
    }

    public PaymentRefundResult findRefund(String key) {
        String path = UriComponentsBuilder.fromPath("/internal/BofA/refunds/by-key")
                .queryParam("refundKey", key).build().encode().toUriString();
        try { return refundExchange(path, HttpMethod.GET, null); }
        catch (HttpClientErrorException.NotFound ex) {
            try {
                var error = new ObjectMapper().readTree(ex.getResponseBodyAsString());
                if (error != null && error.path("status").asInt() == 3016) return null;
            } catch (JsonProcessingException ignored) { }
            throw ex;
        }
    }

    private PaymentRefundResult refundExchange(String path, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());
        RequestCorrelation.add(headers);
        ResponseEntity<ResponseAPI<PaymentRefundResult>> response = restTemplate.exchange(
                properties.getBaseUrl() + path, method, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<ResponseAPI<PaymentRefundResult>>() {});
        if (response.getBody() == null || !java.util.Objects.equals(response.getBody().getCode(), 200)
                || response.getBody().getData() == null) throw new RestClientException("Invalid refund response");
        return response.getBody().getData();
    }

    public PaymentReleaseResult findRelease(String releaseKey) {
        String path = UriComponentsBuilder.fromPath("/internal/BofA/payout/releases/by-key")
                .queryParam("releaseKey", releaseKey).build().encode().toUriString();
        try {
            return releaseExchange(path, HttpMethod.GET, null);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
    }

    private PaymentReleaseResult releaseExchange(String path, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());
        RequestCorrelation.add(headers);
        ResponseEntity<ResponseAPI<PaymentReleaseResult>> response = restTemplate.exchange(
                properties.getBaseUrl() + path, method, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<ResponseAPI<PaymentReleaseResult>>() {});
        if (response.getBody() == null || !java.util.Objects.equals(response.getBody().getCode(), 200)
                || response.getBody().getData() == null) {
            // A malformed 2xx response is ambiguous, not proof of failure/non-existence.
            throw new RestClientException("Invalid release response");
        }
        return response.getBody().getData();
    }

    private <T> T exchange(String path, HttpMethod method, Object body,
                           ParameterizedTypeReference<ResponseAPI<T>> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());
        RequestCorrelation.add(headers);

        try {
            ResponseEntity<ResponseAPI<T>> response = restTemplate.exchange(
                    properties.getBaseUrl() + path,
                    method,
                    new HttpEntity<>(body, headers),
                    type
            );
            return response.getBody() != null ? response.getBody().getData() : null;
        } catch (RestClientException ex) {
            throw new ApplicationException(ErrorCode.PAYMENT_BACKEND_CALL_FAILED, path);
        }
    }
}
