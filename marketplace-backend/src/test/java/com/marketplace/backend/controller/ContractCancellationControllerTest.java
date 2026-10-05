package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.*;
import com.marketplace.backend.configuration.jwt.*;
import com.marketplace.backend.dto.request.cancellation.*;
import com.marketplace.backend.exception.*;
import com.marketplace.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContractCancellationController.class)
@Import({SecurityConfiguration.class, InternalApiKeyFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDenied.class})
@TestPropertySource(properties={"cors.allowed-origins=http://localhost:8080","internal.api.key=test-only-internal-key"})
class ContractCancellationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ContractCancellationService service;
    @MockitoBean UserDetailsServiceCustomizer userDetails;
    @MockitoBean JwtService jwt;
    UUID contract = UUID.randomUUID(), cancellation = UUID.randomUUID(), actor = UUID.randomUUID();
    UserPrincipal principal() { return new UserPrincipal(actor,"test@example.test","hash",Set.of(),true); }
    String path() { return "/api/v1/contracts/" + contract + "/cancellations"; }
    @Test void allPublicOperationsRequireAuthentication() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(path()+"/"+cancellation+"/decisions").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void createPassesAuthenticatedActorAndBusinessIntentOnly() throws Exception {
        mvc.perform(post(path()).with(user(principal())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"MUTUAL\",\"description\":\"Stop work\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).request(actor,contract,new CreateCancellationRequest("MUTUAL","Stop work"));
    }
    @Test void decisionsPassAuthenticatedCounterpartAndExactDecision() throws Exception {
        mvc.perform(post(path()+"/"+cancellation+"/decisions").with(user(principal())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"ACCEPT\"}")).andExpect(status().isOk());
        verify(service).decide(actor,contract,cancellation,new CancellationDecisionRequest(CancellationDecisionRequest.Decision.ACCEPT));
    }
    @Test void readDelegatesParticipantAuthorizationAndHidesUnknownContracts() throws Exception {
        when(service.get(actor,contract)).thenThrow(new ApplicationException(ErrorCode.CANCELLATION_NOT_FOUND));
        mvc.perform(get(path()).with(user(principal()))).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(4035));
    }
    @Test void reasonAndDecisionValidationPrecedesService() throws Exception {
        mvc.perform(post(path()).with(user(principal())).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(path()+"/"+cancellation+"/decisions").with(user(principal())).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void workflowConflictUsesExistingNumericEnvelope() throws Exception {
        when(service.request(eq(actor),eq(contract),any())).thenThrow(new ApplicationException(ErrorCode.CANCELLATION_INELIGIBLE));
        mvc.perform(post(path()).with(user(principal())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"MUTUAL\",\"description\":\"Stop\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(4036));
    }
}
