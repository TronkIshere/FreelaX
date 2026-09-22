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
public class PaypalPayoutClient {

    RestTemplate restTemplate;
    PaypalCheckoutProperties properties;

    public Map<String, Object> sendPayout(String receiverEmail, BigDecimal amountUsd, String senderBatchId) {
        String accessToken = fetchAccessToken();

        Map<String, Object> body = Map.of(
                "sender_batch_header", Map.of(
                        "sender_batch_id", senderBatchId,
                        "email_subject", "You have a payout!"
                ),
                "items", List.of(Map.of(
                        "recipient_type", "EMAIL",
                        "amount", Map.of(
                                "value", amountUsd.toPlainString(),
                                "currency", properties.getCurrency()
                        ),
                        "receiver", receiverEmail,
                        "sender_item_id", senderBatchId
                ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map> response = restTemplate.exchange(
                properties.getBaseUrl() + "/v1/payments/payouts",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        if (response.getBody() == null) {
            throw new ApplicationException(ErrorCode.PAYPAL_PAYOUT_FAILED, senderBatchId);
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
            throw new ApplicationException(ErrorCode.PAYPAL_PAYOUT_FAILED, "oauth");
        }

        return (String) response.getBody().get("access_token");
    }
}
