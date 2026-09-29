package com.marketplace.backend.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class MisaBackendClientTest {

    @Test
    void extractsPayoutIdFromWrappedResponse() {
        MisaBackendClient client = client();
        UUID taxpayerId = UUID.randomUUID();
        UUID payoutId = UUID.randomUUID();
        MockRestServiceServer server = server(client);
        server.expect(once(), requestTo("http://misa.test/api/v1/taxpayers/" + taxpayerId + "/payouts"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"id\":\"" + payoutId + "\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.recordPayoutTransaction(taxpayerId, UUID.randomUUID(),
                new BigDecimal("500"), new BigDecimal("25000"), "signature", "solana").getId())
                .isEqualTo(payoutId);
        server.verify();
    }

    @Test
    void rejectsSuccessResponseWithoutPayoutId() {
        MisaBackendClient client = client();
        UUID taxpayerId = UUID.randomUUID();
        MockRestServiceServer server = server(client);
        server.expect(requestTo("http://misa.test/api/v1/taxpayers/" + taxpayerId + "/payouts"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.recordPayoutTransaction(taxpayerId, UUID.randomUUID(),
                new BigDecimal("500"), new BigDecimal("25000"), "signature", "solana"))
                .isInstanceOf(ApplicationException.class);
        server.verify();
    }

    @Test
    void sendsRequiredDemoTaxpayerIdentityToMisa() {
        MisaBackendClient client = client();
        UUID taxpayerId = UUID.randomUUID();
        MockRestServiceServer server = server(client);
        server.expect(requestTo("http://misa.test/api/v1/taxpayers/external"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.taxCode").value("DEMO-TAX-000001"))
                .andExpect(jsonPath("$.identityNumber").value("DEMO-ID-000001"))
                .andExpect(jsonPath("$.nationality").value("VN"))
                .andExpect(jsonPath("$.address").value("Demo address"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"id\":\"" + taxpayerId + "\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.registerTaxpayerForExternal(UUID.randomUUID(), "Demo Freelancer",
                "DEMO-TAX-000001", "DEMO-ID-000001", "VN", "Demo address"))
                .isEqualTo(taxpayerId);
        server.verify();
    }

    private MisaBackendClient client() {
        MisaBackendClient client = new MisaBackendClient(new RestTemplateBuilder(), new ObjectMapper(), 3000, 10000);
        ReflectionTestUtils.setField(client, "baseUrl", "http://misa.test");
        ReflectionTestUtils.setField(client, "cachedAccessToken", "test-token");
        ReflectionTestUtils.setField(client, "cachedTokenExpiresAt", Instant.now().plusSeconds(3600));
        return client;
    }

    private MockRestServiceServer server(MisaBackendClient client) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }
}
