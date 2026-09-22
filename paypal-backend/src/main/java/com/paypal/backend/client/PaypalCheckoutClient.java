package com.paypal.backend.client;

import com.paypal.backend.configuration.PaypalCheckoutProperties;
import com.paypal.backend.exception.ApplicationException;
import com.paypal.backend.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaypalCheckoutClient {

    RestTemplate restTemplate;
    PaypalCheckoutProperties properties;

    public Map<String, Object> createOrder(BigDecimal amountUsd) {
        String accessToken = fetchAccessToken();

        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "amount", Map.of(
                                "currency_code", properties.getCurrency(),
                                "value", amountUsd.toPlainString()
                        )
                )),
                "application_context", Map.of(
                        "return_url", properties.getReturnUrl(),
                        "cancel_url", properties.getCancelUrl()
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getBaseUrl() + "/v2/checkout/orders",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ApplicationException(ErrorCode.PAYPAL_ORDER_FAILED, "create");
        }

        return response.getBody();
    }

    public Map<String, Object> captureOrder(String paypalOrderId) {
        String accessToken = fetchAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getBaseUrl() + "/v2/checkout/orders/" + paypalOrderId + "/capture",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
        );

        if (response.getBody() == null) {
            throw new ApplicationException(ErrorCode.PAYPAL_ORDER_FAILED, paypalOrderId);
        }

        return response.getBody();
    }

    private String fetchAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(properties.getClientId(), properties.getClientSecret());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");

        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getBaseUrl() + "/v1/oauth2/token",
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                Map.class
        );

        if (response.getBody() == null || response.getBody().get("access_token") == null) {
            throw new ApplicationException(ErrorCode.PAYPAL_ORDER_FAILED, "oauth");
        }

        return (String) response.getBody().get("access_token");
    }
}
