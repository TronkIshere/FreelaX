package com.marketplace.backend.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.backend.configuration.jwt.JwtAccessDenied;
import com.marketplace.backend.configuration.jwt.JwtAuthenticationEntryPoint;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ErrorResponseEncodingTest {

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"UNAUTHENTICATED", "ACCESS_DENIED"})
    void securityWritersPreserveVietnameseEvenWithServletDefaultEncoding(ErrorCode code) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setAttribute(RequestCorrelation.ATTRIBUTE, "encoding-test-request");
        var response = new MockHttpServletResponse();
        // No MVC encoding filter: reproduce the container's default writer charset.
        response.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());

        if (code == ErrorCode.UNAUTHENTICATED) {
            new JwtAuthenticationEntryPoint().commence(request, response,
                    new InsufficientAuthenticationException("Not authenticated"));
        } else {
            new JwtAccessDenied().handle(request, response, new AccessDeniedException("Denied"));
        }

        assertError(response, code);
        assertThat(new ObjectMapper().readTree(response.getContentAsByteArray())
                .path("requestId").asText()).isEqualTo("encoding-test-request");
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"UNAUTHENTICATED", "ACCESS_DENIED"})
    void mvcAdviceAlreadyPreservesUtf8Json(ErrorCode code) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var response = mvc.perform(get(code == ErrorCode.ACCESS_DENIED ? "/denied" : "/unauthenticated"))
                .andReturn().getResponse();

        assertError(response, code);
    }

    @Test
    void internalApiKeyErrorUsesUtf8WithoutChangingItsPayloadOrContinuingChain() throws Exception {
        var request = new MockHttpServletRequest("GET", "/internal/encoding-test");
        var response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        var chain = mock(jakarta.servlet.FilterChain.class);

        new InternalApiKeyFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"code\":401,\"message\":\"Invalid or missing internal API key\"}");
        verifyNoInteractions(chain);
    }

    private void assertError(MockHttpServletResponse response, ErrorCode code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(code.getHttpStatus().value());
        // JSON without a charset is also UTF-8 when written by Jackson's MVC converter.
        assertThat(response.getContentType()).isIn("application/json", "application/json;charset=UTF-8");
        var bytes = response.getContentAsByteArray();
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains(code.getMessage()).doesNotContain("?");
        var body = new ObjectMapper().readTree(bytes);
        assertThat(body.path("error").asText()).isEqualTo(code.getMessage());
        assertThat(body.path("status").asInt()).isEqualTo(code.getCode());
        assertThat(body.path("code").asText()).isEqualTo(code.name());
        assertThat(body.path("requestId").asText()).isNotBlank();
        assertThat(body.path("retryable").isBoolean()).isTrue();
        assertThat(body.path("retryable").asBoolean()).isFalse();
    }

    @RestController
    static class ErrorController {
        @GetMapping("/denied")
        void denied() { throw new AccessDeniedException("Denied"); }

        @GetMapping("/unauthenticated")
        void unauthenticated() { throw new ApplicationException(ErrorCode.UNAUTHENTICATED); }
    }
}
