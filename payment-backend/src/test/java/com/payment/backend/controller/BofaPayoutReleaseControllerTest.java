package com.payment.backend.controller;

import com.payment.backend.configuration.InternalApiKeyFilter;
import com.payment.backend.dto.request.bofa.CreatePayoutReleaseRequest;
import com.payment.backend.dto.response.bofa.BofaPayoutReleaseResponse;
import com.payment.backend.exception.ApplicationException;
import com.payment.backend.exception.ErrorCode;
import com.payment.backend.exception.GlobalExceptionHandler;
import com.payment.backend.service.BofaPayoutReleaseService;
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

class BofaPayoutReleaseControllerTest {
    private static final String PATH = "/internal/BofA/payout/releases";
    private static final String TEST_KEY = "test-only-internal-key";
    private final UUID checkout = UUID.randomUUID();
    private final UUID recipient = UUID.randomUUID();
    private BofaPayoutReleaseService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(BofaPayoutReleaseService.class);
        InternalApiKeyFilter filter = new InternalApiKeyFilter();
        ReflectionTestUtils.setField(filter, "expectedApiKey", TEST_KEY);
        mvc = MockMvcBuilders.standaloneSetup(new BofaPayoutReleaseController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(filter).build();
    }

    @Test
    void internalReleaseReturnsOnlySafeTypedEnvelope() throws Exception {
        when(service.release(any())).thenReturn(response());
        mvc.perform(post(PATH).header("X-Internal-Api-Key", TEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body("500.00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.simulation").value(true))
                .andExpect(jsonPath("$.data.amount").value("500.00"))
                .andExpect(jsonPath("$.data.bankAccountNumber").doesNotExist())
                .andExpect(jsonPath("$.data.payloadHash").doesNotExist());
        verify(service).release(new CreatePayoutReleaseRequest(checkout, recipient,
                new CreatePayoutReleaseRequest.ExpectedAmount(new BigDecimal("500.00"), "USD"), "release-1"));
    }

    @Test
    void lookupByKeyIsReadOnly() throws Exception {
        when(service.getByReleaseKey("release-1")).thenReturn(response());
        mvc.perform(get(PATH + "/by-key").param("releaseKey", "release-1")
                        .header("X-Internal-Api-Key", TEST_KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.releaseKey").value("release-1"));
        verify(service).getByReleaseKey("release-1");
        verify(service, never()).release(any());
    }

    @Test
    void lookupByCheckoutIsReadOnly() throws Exception {
        when(service.getByCheckoutOrderId(checkout)).thenReturn(response());
        mvc.perform(get(PATH + "/by-checkout/" + checkout).header("X-Internal-Api-Key", TEST_KEY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.checkoutOrderId").value(checkout.toString()));
        verify(service).getByCheckoutOrderId(checkout);
        verify(service, never()).release(any());
    }

    @Test
    void missingKeyProtectsEveryNewRoute() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("500.00")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/by-key").param("releaseKey", "release-1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH + "/by-checkout/" + checkout)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void invalidKeyAndBearerAloneDoNotAuthorizeRelease() throws Exception {
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
        when(service.release(any())).thenThrow(new ApplicationException(ErrorCode.RELEASE_KEY_CONFLICT));
        mvc.perform(post(PATH).header("X-Internal-Api-Key", TEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body("500.00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(3014));
    }

    @Test
    void missingReleaseLookupReturns404() throws Exception {
        when(service.getByReleaseKey("missing")).thenThrow(new ApplicationException(ErrorCode.PAYOUT_RELEASE_NOT_FOUND));
        mvc.perform(get(PATH + "/by-key").param("releaseKey", "missing")
                        .header("X-Internal-Api-Key", TEST_KEY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(3008));
    }

    private String body(String amount) {
        return "{\"checkoutOrderId\":\"" + checkout + "\",\"recipientUserId\":\"" + recipient
                + "\",\"expectedAmount\":{\"amount\":\"" + amount + "\",\"currency\":\"USD\"},\"releaseKey\":\"release-1\"}";
    }
    private BofaPayoutReleaseResponse response() {
        return new BofaPayoutReleaseResponse(UUID.randomUUID(), "release-1", checkout, recipient,
                "SUCCEEDED", new BigDecimal("500.00"), "USD", true, "sim-release-test", false,
                Instant.parse("2026-10-05T08:00:00Z"), Instant.parse("2026-10-05T08:00:00Z"));
    }
}
