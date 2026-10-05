package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.settlement.SettlementResponse;
import com.marketplace.backend.entity.ContractSettlement;
import com.marketplace.backend.exception.*;
import com.marketplace.backend.service.SettlementService;
import org.junit.jupiter.api.*;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Route/envelope tests; participant authorization is exercised with real DB in SettlementServiceTest.
class SettlementControllerTest {
    SettlementService service;
    MockMvc mvc;
    UUID participant = UUID.randomUUID(), contract = UUID.randomUUID();

    @BeforeEach void setup() {
        service = mock(SettlementService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SettlementController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) { return parameter.hasParameterAnnotation(AuthenticationPrincipal.class); }
                    public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest r, WebDataBinderFactory f) {
                        return new UserPrincipal(participant, "participant@test.invalid", "", Set.of(), true);
                    }
                }).build();
    }

    @Test void publicReadReturnsExistingEnvelopeAndIndependentStatuses() throws Exception {
        ContractSettlement s = new ContractSettlement(); s.setContractId(contract); s.setMilestoneId(UUID.randomUUID());
        s.setJobId(UUID.randomUUID()); s.setAmount(new BigDecimal("500.00")); s.setCurrency("USD");
        when(service.get(participant, contract)).thenReturn(SettlementResponse.from(s));
        mvc.perform(get("/api/v1/contracts/{contractId}/settlement", contract)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.contractId").value(contract.toString()))
                .andExpect(jsonPath("$.data.moneyStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.onChainStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.offRampStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.taxStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.simulation").value(true)).andExpect(jsonPath("$.data.releaseKey").doesNotExist());
    }

    @Test void unrelatedParticipantGetsNotFoundWithoutIdentityDisclosure() throws Exception {
        when(service.get(participant, contract)).thenThrow(new ApplicationException(ErrorCode.SETTLEMENT_NOT_FOUND));
        mvc.perform(get("/api/v1/contracts/{contractId}/settlement", contract)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(4033));
    }

    @Test void normalUserCannotPostMoneyRelease() throws Exception {
        mvc.perform(post("/api/v1/contracts/{contractId}/settlement", contract))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(org.springframework.web.HttpRequestMethodNotSupportedException.class));
        verifyNoInteractions(service);
    }
}
