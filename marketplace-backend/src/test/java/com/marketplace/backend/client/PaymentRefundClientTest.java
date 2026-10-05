package com.marketplace.backend.client;

import com.marketplace.backend.configuration.PaymentBackendProperties;
import com.marketplace.backend.dto.request.bofa.CreateRefundRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PaymentRefundClientTest {
    PaymentBackendClient client;
    MockRestServiceServer server;
    UUID checkout = UUID.randomUUID(), recipient = UUID.randomUUID(), refund = UUID.randomUUID();
    String key = "marketplace-settlement-" + UUID.randomUUID();

    @BeforeEach void setup() {
        RestTemplate rest = new RestTemplate(); server = MockRestServiceServer.bindTo(rest).build();
        var config = new PaymentBackendProperties(); config.setBaseUrl("http://payment.test"); config.setInternalApiKey("test-only-internal-key");
        client = new PaymentBackendClient(rest, config);
    }
    @AfterEach void verified() { server.verify(); }

    @Test void createUsesActualStep40EndpointHeaderAndStringAmount() {
        server.expect(requestTo("http://payment.test/internal/BofA/refunds"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-Internal-Api-Key", "test-only-internal-key"))
                .andExpect(request -> assertThat(UUID.fromString(request.getHeaders().getFirst("X-Request-Id"))).isNotNull())
                .andExpect(content().json("""
                        {"checkoutOrderId":"%s","expectedAmount":{"amount":"500.00","currency":"USD"},"refundKey":"%s"}
                        """.formatted(checkout, key)))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
        var result = client.createRefund(new CreateRefundRequest(checkout,
                new CreateRefundRequest.ExpectedAmount("500.00", "USD"), key));
        assertThat(result.amount()).isEqualByComparingTo("500.00"); assertThat(result.refundId()).isEqualTo(refund);
        assertThat(result.refundReference()).isEqualTo("sim-refund-" + refund); assertThat(result.simulation()).isTrue();
    }

    @Test void lookupUsesOriginalKeyAndInternalAuthentication() {
        server.expect(requestTo(lookup())).andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Api-Key", "test-only-internal-key"))
                .andRespond(withSuccess(body(), MediaType.APPLICATION_JSON));
        assertThat(client.findRefund(key).refundKey()).isEqualTo(key);
    }

    @Test void only404MeansNoRefundExists() {
        server.expect(requestTo(lookup())).andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"status\":3016}").contentType(MediaType.APPLICATION_JSON));
        assertThat(client.findRefund(key)).isNull();
    }

    @Test void emptySuccessCannotBeMistakenForMissingRefund() {
        server.expect(requestTo(lookup())).andRespond(withSuccess("{\"code\":200}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.findRefund(key)).isInstanceOf(RestClientException.class);
    }

    @Test void lookupAuthorizationErrorIsPreservedNotConvertedToAbsence() {
        server.expect(requestTo(lookup())).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.findRefund(key)).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test void routing404IsNotProofOfAbsence() {
        server.expect(requestTo(lookup())).andRespond(withResourceNotFound());
        assertThatThrownBy(() -> client.findRefund(key)).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    private String lookup() { return "http://payment.test/internal/BofA/refunds/by-key?refundKey=" + key; }
    private String body() {
        return """
                {"code":200,"data":{"refundId":"%s","refundKey":"%s","checkoutOrderId":"%s","payerUserId":"%s",
                "status":"SUCCEEDED","amount":"500.00","currency":"USD","simulation":true,"refundReference":"sim-refund-%s",
                "retryable":false,"createdAt":"2026-10-05T08:00:00Z","updatedAt":"2026-10-05T08:00:00Z"}}
                """.formatted(refund, key, checkout, recipient, refund);
    }
}
