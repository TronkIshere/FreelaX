package com.paypal.backend.client;

import com.paypal.backend.configuration.PaypalCheckoutProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaypalCheckoutClient {

    PaypalCheckoutProperties properties;

    RestTemplate restTemplate = new RestTemplate();

    public String fetchAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        String credentials = properties.getClientId() + ":" + properties.getClientSecret();
        headers.set(HttpHeaders.AUTHORIZATION,
                "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()));
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        Map<?, ?> response = restTemplate.postForObject(
                properties.getBaseUrl() + "/v1/oauth2/token", request, Map.class);
        return (String) response.get("access_token");
    }

    public Map<String, Object> createOrder(BigDecimal amountUsd) {
        String accessToken = fetchAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> amount = Map.of(
                "currency_code", "USD",
                "value", amountUsd.toPlainString());
        Map<String, Object> purchaseUnit = Map.of("amount", amount);
        Map<String, Object> applicationContext = Map.of(
                "return_url", properties.getReturnUrl(),
                "cancel_url", properties.getCancelUrl());
        Map<String, Object> orderRequest = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(purchaseUnit),
                "application_context", applicationContext);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(orderRequest, headers);
        return (Map<String, Object>) (Map) restTemplate.postForObject(
                properties.getBaseUrl() + "/v2/checkout/orders", request, Map.class);
    }

    public Map<String, Object> captureOrder(String orderId) {
        String accessToken = fetchAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> request = new HttpEntity<>(headers);
        return (Map<String, Object>) (Map) restTemplate.postForObject(
                properties.getBaseUrl() + "/v2/checkout/orders/" + orderId + "/capture",
                request, Map.class);
    }
}
