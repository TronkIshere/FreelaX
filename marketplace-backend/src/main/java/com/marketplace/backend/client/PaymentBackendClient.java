package com.marketplace.backend.client;

import com.marketplace.backend.configuration.PaymentBackendProperties;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.bofa.CheckoutOrderResult;
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
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentBackendClient {

    RestTemplate restTemplate;
    PaymentBackendProperties properties;

    public CheckoutOrderResult createCheckoutOrder(UUID payerUserId, UUID jobId, BigDecimal amountUsd) {
        Map<String, Object> body = Map.of(
                "payerUserId", payerUserId,
                "jobId", jobId,
                "amountUsd", amountUsd
        );
        return exchange(
                "/internal/BofA/checkout/orders",
                HttpMethod.POST,
                body,
                new ParameterizedTypeReference<ResponseAPI<CheckoutOrderResult>>() {}
        );
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

    private <T> T exchange(String path, HttpMethod method, Object body,
                           ParameterizedTypeReference<ResponseAPI<T>> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());

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