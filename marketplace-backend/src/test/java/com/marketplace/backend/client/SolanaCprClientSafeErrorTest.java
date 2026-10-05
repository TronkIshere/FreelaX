package com.marketplace.backend.client;

import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.exception.SolanaCprException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class SolanaCprClientSafeErrorTest {
    @Test
    void providerBodyIsNeverStoredInExceptionMessage() {
        var properties = new SolanaCprProperties();
        properties.setBaseUrl("http://solana.test");
        var client = new SolanaCprClient(new RestTemplateBuilder(), properties);
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "restTemplate")).build();
        server.expect(requestTo("http://solana.test/api/v1/solana/transactions/signature?commitment=confirmed"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("token=secret-provider-value"));

        assertThatThrownBy(() -> client.getTransactionStatus("signature"))
                .isInstanceOf(SolanaCprException.class)
                .hasMessageContaining("HTTP 502")
                .hasMessageNotContaining("secret-provider-value");
        server.verify();
    }
}
