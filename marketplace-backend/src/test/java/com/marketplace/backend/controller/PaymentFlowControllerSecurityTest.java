package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.*;
import com.marketplace.backend.configuration.jwt.*;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentFlowController.class)
@Import({SecurityConfiguration.class, InternalApiKeyFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDenied.class})
@TestPropertySource(properties = {"cors.allowed-origins=http://localhost:8080", "internal.api.key=test-only-internal-key"})
class PaymentFlowControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean PaymentFlowService flows;
    @MockitoBean UnifiedUsdFundingService funding;
    @MockitoBean UnifiedExitService exit;
    @MockitoBean UnifiedReconciliationService reconciliation;
    @MockitoBean UnifiedFundingExpiryService expiry;
    @MockitoBean UnifiedLedgerSummaryService ledger;
    @MockitoBean UserDetailsServiceCustomizer userDetails;
    @MockitoBean JwtService jwt;
    final UUID actor = UUID.randomUUID(), flow = UUID.randomUUID(), contract = UUID.randomUUID(), milestone = UUID.randomUUID();

    UserPrincipal principal(String role) {
        Role r = new Role(); r.setName(role);
        return new UserPrincipal(actor, "user@example.test", "hash", Set.of(r), true);
    }

    List<MockHttpServletRequestBuilder> adminCalls() {
        return List.of(get("/api/v1/admin/payment-flows/reconciliation"),
                get("/api/v1/admin/payment-flows/summary"),
                get("/api/v1/admin/contracts/" + contract + "/milestones/" + milestone + "/payment-flow"),
                post("/api/v1/admin/payment-flows/" + flow + "/reconciliation-reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boundary\":\"USD_TO_CLIENT_USDC\",\"decision\":\"ESCALATED\",\"note\":\"Checked provider\"}"),
                post("/api/v1/admin/contracts/" + contract + "/payment-flow/expired-funding-cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Expired after on-ramp\"}"));
    }

    @Test void anonymousAndParticipantsCannotUseAdminMoneyEndpoints() throws Exception {
        for (MockHttpServletRequestBuilder call : adminCalls()) {
            mvc.perform(call).andExpect(status().isUnauthorized());
            mvc.perform(call.with(user(principal("ROLE_USER")))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(reconciliation, expiry, ledger);
        verify(flows, never()).timeline(any(), any(), any(), eq(true));
    }

    @Test void adminCallsCarryTheAuthenticatedAdminIdentity() throws Exception {
        for (MockHttpServletRequestBuilder call : adminCalls())
            mvc.perform(call.with(user(principal("ROLE_ADMIN")))).andExpect(status().isOk());
        verify(reconciliation).review(eq(actor), eq(flow), eq("USD_TO_CLIENT_USDC"), eq("ESCALATED"), eq("Checked provider"));
        verify(expiry).cancelExpired(eq(actor), eq(contract), eq("Expired after on-ramp"));
        verify(flows).timeline(eq(actor), eq(contract), eq(milestone), eq(true));
    }

    @Test void participantEndpointsRequireAuthenticationAndPassTheCallerNotABodyField() throws Exception {
        String base = "/api/v1/contracts/" + contract + "/milestones/" + milestone + "/payment-flow";
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(post(base + "/withdrawal/prepare")).andExpect(status().isUnauthorized());
        mvc.perform(get(base).with(user(principal("ROLE_USER")))).andExpect(status().isOk());
        verify(flows).timeline(eq(actor), eq(contract), eq(milestone), eq(false));
        mvc.perform(post(base + "/usd-order").header("Idempotency-Key", "k-1").with(user(principal("ROLE_USER"))))
                .andExpect(status().isOk());
        verify(funding).openUsdOrder(eq(actor), eq(contract), eq(milestone), eq("k-1"));
        mvc.perform(post(base + "/withdrawal/prepare").with(user(principal("ROLE_USER")))).andExpect(status().isOk());
        verify(exit).prepare(eq(actor), eq(contract), eq(milestone));
    }
}
