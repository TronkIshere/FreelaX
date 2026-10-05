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

    @Test void certificateCreateSendsStableKeyAndPayout() {
        var client = client(); var server = server(client); UUID payout = UUID.randomUUID(); UUID certificate = UUID.randomUUID();
        server.expect(requestTo("http://misa.test/api/v1/withholding-certificates"))
                .andExpect(method(POST)).andExpect(jsonPath("$.payoutTransactionId").value(payout.toString()))
                .andExpect(jsonPath("$.idempotencyKey").value("stable-key"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"id\":\"" + certificate + "\",\"status\":\"DRAFT\"}}", MediaType.APPLICATION_JSON));
        assertThat(client.createWithholdingCertificate(payout, "stable-key").getId()).isEqualTo(certificate.toString()); server.verify();
    }

    @Test void lookupAuthenticatesAndReturnsTypedRecovery() {
        var client = client(); var server = server(client); UUID payout = UUID.randomUUID(), certificate = UUID.randomUUID();
        server.expect(requestTo("http://misa.test/api/v1/withholding-certificates/by-platform-payout/" + payout))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"code\":200,\"data\":{\"certificateId\":\"" + certificate + "\",\"platformPayoutId\":\"" + payout + "\",\"simulation\":true}}", MediaType.APPLICATION_JSON));
        assertThat(client.findCertificateByPlatformPayout(payout).certificateId()).isEqualTo(certificate); server.verify();
    }

    @Test void onlyCertificateNotFoundErrorProvesAbsence() {
        var client = client(); var server = server(client); UUID payout = UUID.randomUUID();
        server.expect(requestTo("http://misa.test/api/v1/withholding-certificates/by-platform-payout/" + payout))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"status\":3004}"));
        assertThat(client.findCertificateByPlatformPayout(payout)).isNull(); server.verify();
    }

    @Test void unknownRoute404IsNotCertificateAbsence() {
        var client = client(); var server = server(client); UUID payout = UUID.randomUUID();
        server.expect(requestTo("http://misa.test/api/v1/withholding-certificates/by-platform-payout/" + payout))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"status\":404}"));
        assertThatThrownBy(() -> client.findCertificateByPlatformPayout(payout)).isInstanceOf(org.springframework.web.client.HttpClientErrorException.NotFound.class); server.verify();
    }

    @Test void malformedSuccessIsUnresolved() {
        var client = client(); var server = server(client); UUID payout = UUID.randomUUID();
        server.expect(requestTo("http://misa.test/api/v1/withholding-certificates/by-platform-payout/" + payout))
                .andRespond(withSuccess("{\"code\":200,\"data\":null}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.findCertificateByPlatformPayout(payout)).isInstanceOf(org.springframework.web.client.RestClientException.class); server.verify();
    }

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
