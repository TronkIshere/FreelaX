package com.marketplace.backend.client;

import com.marketplace.backend.configuration.PaymentBackendProperties;
import com.marketplace.backend.dto.request.bofa.CreateReleaseRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PaymentReleaseClientTest {
    PaymentBackendClient client;
    MockRestServiceServer server;
    UUID checkout = UUID.randomUUID(), recipient = UUID.randomUUID(), release = UUID.randomUUID();
    String key = "marketplace-settlement-" + UUID.randomUUID();

    @BeforeEach void setup() {
        RestTemplate rest = new RestTemplate(); server = MockRestServiceServer.bindTo(rest).build();
        var config = new PaymentBackendProperties(); config.setBaseUrl("http://payment.test"); config.setInternalApiKey("test-only-internal-key");
        client = new PaymentBackendClient(rest, config);
    }
    @AfterEach void verified() { server.verify(); }

    @Test void createUsesActualStep40EndpointHeaderAndStringAmount() {
        server.expect(requestTo("http://payment.test/internal/BofA/payout/releases"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-Internal-Api-Key", "test-only-internal-key"))
                .andExpect(content().json("""
                        {"checkoutOrderId":"%s","recipientUserId":"%s","expectedAmount":{"amount":"500.00","currency":"USD"},"releaseKey":"%s"}
                        """.formatted(checkout, recipient, key)))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
        var result = client.createRelease(new CreateReleaseRequest(checkout, recipient,
                new CreateReleaseRequest.ExpectedAmount("500.00", "USD"), key));
        assertThat(result.amount()).isEqualByComparingTo("500.00"); assertThat(result.releaseId()).isEqualTo(release);
        assertThat(result.releaseReference()).isEqualTo("sim-release-" + release); assertThat(result.simulation()).isTrue();
    }

    @Test void lookupUsesOriginalKeyAndInternalAuthentication() {
        server.expect(requestTo(lookup())).andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Api-Key", "test-only-internal-key"))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
        assertThat(client.findRelease(key).releaseKey()).isEqualTo(key);
    }

    @Test void only404MeansNoReleaseExists() {
        server.expect(requestTo(lookup())).andRespond(withResourceNotFound());
        assertThat(client.findRelease(key)).isNull();
    }

    @Test void emptySuccessCannotBeMistakenForMissingRelease() {
        server.expect(requestTo(lookup())).andRespond(withSuccess("{\"code\":200}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.findRelease(key)).isInstanceOf(RestClientException.class);
    }

    @Test void lookupAuthorizationErrorIsPreservedNotConvertedToAbsence() {
        server.expect(requestTo(lookup())).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.findRelease(key)).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    private String lookup() { return "http://payment.test/internal/BofA/payout/releases/by-key?releaseKey=" + key; }
    private String body() {
        return """
                {"code":200,"data":{"releaseId":"%s","releaseKey":"%s","checkoutOrderId":"%s","recipientUserId":"%s",
                "status":"SUCCEEDED","amount":"500.00","currency":"USD","simulation":true,"releaseReference":"sim-release-%s",
                "retryable":false,"createdAt":"2026-10-05T08:00:00Z","updatedAt":"2026-10-05T08:00:00Z"}}
                """.formatted(release, key, checkout, recipient, release);
    }
}
