package com.misa.backend.controller;

import com.misa.backend.configuration.SecurityConfiguration;
import com.misa.backend.configuration.jwt.*;
import com.misa.backend.dto.response.misa.CertificateRecoveryResponse;
import com.misa.backend.exception.*;
import com.misa.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WithholdingCertificateController.class)
@ContextConfiguration(classes = CertificateRecoveryApiTest.Config.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:8080")
class CertificateRecoveryApiTest {
    @Configuration
    @Import({WithholdingCertificateController.class, GlobalExceptionHandler.class, SecurityConfiguration.class,
            JwtAuthenticationEntryPoint.class, JwtAccessDenied.class})
    static class Config { }
    @Autowired MockMvc mvc;
    @MockitoBean WithholdingCertificateService service;
    @MockitoBean JwtService jwt;
    @MockitoBean UserDetailsServiceCustomizer users;

    @Test void recoveryAndCreateRequireExistingServiceAuthentication() throws Exception {
        mvc.perform(get("/api/v1/withholding-certificates/by-platform-payout/test-payout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/withholding-certificates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"payoutTransactionId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void authenticatedRecoveryReturnsSafeEnvelope() throws Exception {
        UUID cert = UUID.randomUUID(), payout = UUID.randomUUID();
        when(service.findByPlatformPayout("test-payout")).thenReturn(new CertificateRecoveryResponse(cert, payout,
                "test-payout", "stable", "ACCEPTED", "0001", "DEMO", new BigDecimal("100"), new BigDecimal("25000"),
                new BigDecimal("2500000"), new BigDecimal("250000"), "VND", LocalDateTime.now(), null, true));
        mvc.perform(get("/api/v1/withholding-certificates/by-platform-payout/test-payout").with(user("platform")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.certificateId").value(cert.toString()))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.simulation").value(true))
                .andExpect(jsonPath("$.data.taxpayer").doesNotExist()).andExpect(jsonPath("$.data.digitalCertificateSerial").doesNotExist());
    }

    @Test void missingRecoveryReturnsSpecific404() throws Exception {
        when(service.findByPlatformPayout("missing")).thenThrow(new ApplicationException(ErrorCode.CERTIFICATE_NOT_FOUND, "missing"));
        mvc.perform(get("/api/v1/withholding-certificates/by-platform-payout/missing").with(user("platform")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(3004));
    }

    @Test void uniqueKeyRaceReturnsMachineReadableConflict() throws Exception {
        when(service.create(any())).thenThrow(new DataIntegrityViolationException("private DB message"));
        mvc.perform(post("/api/v1/withholding-certificates").with(user("platform"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"payoutTransactionId\":\"" + UUID.randomUUID() + "\",\"idempotencyKey\":\"stable\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(3010));
    }
}
