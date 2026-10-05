package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.*;
import com.marketplace.backend.configuration.jwt.*;
import com.marketplace.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContractSubmissionController.class)
@Import({SecurityConfiguration.class, InternalApiKeyFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDenied.class})
@TestPropertySource(properties={"cors.allowed-origins=http://localhost:8080","internal.api.key=test-only-internal-key"})
class ContractSubmissionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ContractSubmissionService service;
    @MockitoBean UserDetailsServiceCustomizer userDetails;
    @MockitoBean JwtService jwt;
    UUID actor = UUID.randomUUID(), contract = UUID.randomUUID(), submission = UUID.randomUUID();

    @Test void minimalDisputeAcceptsTwoThousandCharacterDescription() throws Exception {
        String description = "d".repeat(2000);
        decide(description).andExpect(status().isOk());
        verify(service).decide(eq(actor), eq(contract), eq(submission),
                argThat(request -> description.equals(request.getDescription())));
    }

    @Test void minimalDisputeRejectsOversizedDescriptionBeforeCallingService() throws Exception {
        decide("d".repeat(2001)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATA"));
        verifyNoInteractions(service);
    }

    private org.springframework.test.web.servlet.ResultActions decide(String description) throws Exception {
        UserPrincipal principal = new UserPrincipal(actor, "client@example.test", "hash", Set.of(), true);
        return mvc.perform(post("/api/v1/contracts/" + contract + "/submissions/" + submission + "/decisions")
                .with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"OPEN_DISPUTE\",\"reasonCode\":\"QUALITY\",\"description\":\"" + description + "\"}"));
    }
}
