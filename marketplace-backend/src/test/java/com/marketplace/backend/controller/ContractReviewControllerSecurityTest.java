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

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContractReviewController.class)
@Import({SecurityConfiguration.class, InternalApiKeyFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDenied.class})
@TestPropertySource(properties={"cors.allowed-origins=http://localhost:8080","internal.api.key=test-only-internal-key"})
class ContractReviewControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ContractReviewService service;
    @MockitoBean UserDetailsServiceCustomizer userDetails;
    @MockitoBean JwtService jwt;
    UUID contract = UUID.randomUUID(), actor = UUID.randomUUID();
    UserPrincipal principal(boolean admin) {
        Role role = new Role(); role.setName(admin ? "ROLE_ADMIN" : "ROLE_USER");
        return new UserPrincipal(actor, "reviewer@example.test", "hash", Set.of(role), true);
    }
    String path() { return "/api/v1/contracts/" + contract + "/reviews"; }

    @Test void adminCannotRateAndAnonymousCannotSubmit() throws Exception {
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path()).with(user(principal(true))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"overall\":5,\"dimensions\":{\"communication\":5,\"requirementsOrQuality\":5,\"timeliness\":5}}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void authenticatedNonAdminUsesServerIdentity() throws Exception {
        mvc.perform(post(path()).with(user(principal(false))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"overall\":5,\"dimensions\":{\"communication\":5,\"requirementsOrQuality\":5,\"timeliness\":5}}"))
                .andExpect(status().isOk());
        verify(service).submit(eq(actor), eq(contract), any());
    }
}
