package com.marketplace.backend.configuration;

import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationTest {
    @Test
    void fallbackIdIsStableForStandaloneErrorHandling() {
        var request = new MockHttpServletRequest();
        assertThat(RequestCorrelation.id(request)).isEqualTo(RequestCorrelation.id(request));
    }

    @Test
    void requestIdIsGeneratedReturnedAndPropagatedToInternalHeaders() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/contracts");
        request.addHeader(RequestCorrelation.HEADER, "untrusted-value");
        var response = new MockHttpServletResponse();
        new RequestCorrelationFilter().doFilter(request, response, (req, res) -> {
            String id = RequestCorrelation.id(request);
            assertThat(UUID.fromString(id)).isNotNull();
            assertThat(id).isNotEqualTo("untrusted-value");
            var outbound = new HttpHeaders();
            RequestCorrelation.add(outbound);
            assertThat(outbound.getFirst(RequestCorrelation.HEADER)).isEqualTo(id);
        });
        assertThat(response.getHeader(RequestCorrelation.HEADER)).isEqualTo(RequestCorrelation.id(request));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void errorsHaveMachineCodeAndSafeRequestIdWithoutExceptionDetails() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/contracts");
        var response = new MockHttpServletResponse();
        new RequestCorrelationFilter().doFilter(request, response, (req, res) -> {
            var advice = new GlobalExceptionHandler();
            var gateway = advice.handleApplicationException(
                    new ApplicationException(ErrorCode.PAYMENT_BACKEND_CALL_FAILED, "release"), request).getBody();
            assertThat(gateway).isNotNull();
            assertThat(gateway.getCode()).isEqualTo("PAYMENT_BACKEND_CALL_FAILED");
            assertThat(gateway.getRetryable()).isFalse();
            assertThat(gateway.getRequestId()).isEqualTo(RequestCorrelation.id(request));
            var generic = advice.handleGenericException(new IllegalStateException("bank-secret-123"), request).getBody();
            assertThat(generic).isNotNull();
            assertThat(generic.getError()).doesNotContain("bank-secret-123");
            assertThat(generic.getCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(generic.getRetryable()).isFalse();
        });
    }
}
