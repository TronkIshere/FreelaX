package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.*;
import com.marketplace.backend.configuration.jwt.*;
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

@WebMvcTest(ContractDisputeController.class)
@Import({SecurityConfiguration.class, InternalApiKeyFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDenied.class})
@TestPropertySource(properties={"cors.allowed-origins=http://localhost:8080","internal.api.key=test-only-internal-key"})
class ContractDisputeControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ContractDisputeService service;
    @MockitoBean UserDetailsServiceCustomizer userDetails;
    @MockitoBean JwtService jwt;
    UUID contract = UUID.randomUUID(), actor = UUID.randomUUID();
    UserPrincipal principal() { return new UserPrincipal(actor, "participant@example.test", "hash", Set.of(), true); }
    String path() { return "/api/v1/contracts/" + contract + "/disputes"; }
    @Test void requiresAuthenticationAndUsesPrincipalIdentity() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(path()).with(user(principal())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"QUALITY\",\"description\":\"Work differs\"}")).andExpect(status().isOk());
        verify(service).open(eq(actor), eq(contract), any());
    }
    @Test void outsiderReadIsHidden() throws Exception {
        when(service.get(actor, contract)).thenThrow(new ApplicationException(ErrorCode.DISPUTE_NOT_FOUND));
        mvc.perform(get(path()).with(user(principal()))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(4038));
    }
    @Test void acceptsTwoThousandCharacterDescription() throws Exception {
        String description = "d".repeat(2000);
        mvc.perform(post(path()).with(user(principal())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"QUALITY\",\"description\":\"" + description + "\"}"))
                .andExpect(status().isOk());
        verify(service).open(eq(actor), eq(contract), argThat(request -> description.equals(request.description())));
    }
    @Test void rejectsOversizedAndBlankDescriptionBeforeCallingService() throws Exception {
        for (String description : List.of("d".repeat(2001), "", "   ")) {
            mvc.perform(post(path()).with(user(principal())).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reasonCode\":\"QUALITY\",\"description\":\"" + description + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATA"));
        }
        mvc.perform(post(path()).with(user(principal())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"QUALITY\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
