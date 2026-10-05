package com.payment.backend.controller;

import com.payment.backend.configuration.InternalApiKeyFilter;
import com.payment.backend.dto.request.bofa.CreateCheckoutRefundRequest;
import com.payment.backend.dto.response.bofa.BofaCheckoutRefundResponse;
import com.payment.backend.exception.ApplicationException;
import com.payment.backend.exception.ErrorCode;
import com.payment.backend.exception.GlobalExceptionHandler;
import com.payment.backend.service.BofaCheckoutRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BofaCheckoutRefundControllerTest {
    private static final String PATH = "/internal/BofA/refunds";
    private static final String TEST_KEY = "test-only-internal-key";
    private final UUID checkout = UUID.randomUUID();
    private final UUID recipient = UUID.randomUUID();
    private BofaCheckoutRefundService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(BofaCheckoutRefundService.class);
        InternalApiKeyFilter filter = new InternalApiKeyFilter();
        ReflectionTestUtils.setField(filter, "expectedApiKey", TEST_KEY);
        mvc = MockMvcBuilders.standaloneSetup(new BofaCheckoutRefundController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(filter).build();
    }

    @Test
    void internalRefundReturnsOnlySafeTypedEnvelope() throws Exception {
        when(service.refund(any())).thenReturn(response());
        mvc.perform(post(PATH).header("X-Internal-Api-Key", TEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body("500.00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.simulation").value(true))
                .andExpect(jsonPath("$.data.amount").value("500.00"))
                .andExpect(jsonPath("$.data.bankAccountNumber").doesNotExist())
                .andExpect(jsonPath("$.data.payloadHash").doesNotExist());
        verify(service).refund(new CreateCheckoutRefundRequest(checkout,
                new CreateCheckoutRefundRequest.ExpectedAmount(new BigDecimal("500.00"), "USD"), "refund-1"));
    }

    @Test
    void lookupByKeyIsReadOnly() throws Exception {
        when(service.getByRefundKey("refund-1")).thenReturn(response());
        mvc.perform(get(PATH + "/by-key").param("refundKey", "refund-1")
                        .header("X-Internal-Api-Key", TEST_KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refundKey").value("refund-1"));
        verify(service).getByRefundKey("refund-1");
        verify(service, never()).refund(any());
    }

    @Test
    void missingKeyProtectsEveryNewRoute() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("500.00")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/by-key").param("refundKey", "refund-1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void invalidKeyAndBearerAloneDoNotAuthorizeRefund() throws Exception {
        mvc.perform(post(PATH).header("X-Internal-Api-Key", "wrong-test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body("500.00")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("Authorization", "Bearer test-only-user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body("500.00")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void nestedAmountValidationRejectsBeforeService() throws Exception {
        mvc.perform(post(PATH).header("X-Internal-Api-Key", TEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body("500.001")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(1000));
        verifyNoInteractions(service);
    }

    @Test
    void conflictUsesExistingNumericErrorEnvelope() throws Exception {
        when(service.refund(any())).thenThrow(new ApplicationException(ErrorCode.REFUND_KEY_CONFLICT));
        mvc.perform(post(PATH).header("X-Internal-Api-Key", TEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body("500.00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(3017));
    }

    @Test
    void missingRefundLookupReturns404() throws Exception {
        when(service.getByRefundKey("missing")).thenThrow(new ApplicationException(ErrorCode.REFUND_NOT_FOUND));
        mvc.perform(get(PATH + "/by-key").param("refundKey", "missing")
                        .header("X-Internal-Api-Key", TEST_KEY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(3016));
    }

    private String body(String amount) {
        return "{\"checkoutOrderId\":\"" + checkout
                + "\",\"expectedAmount\":{\"amount\":\"" + amount + "\",\"currency\":\"USD\"},\"refundKey\":\"refund-1\"}";
    }
    private BofaCheckoutRefundResponse response() {
        return new BofaCheckoutRefundResponse(UUID.randomUUID(), "refund-1", checkout, recipient,
                "SUCCEEDED", new BigDecimal("500.00"), "USD", true, "sim-refund-test", false,
                Instant.parse("2026-10-05T08:00:00Z"), Instant.parse("2026-10-05T08:00:00Z"));
    }
}
