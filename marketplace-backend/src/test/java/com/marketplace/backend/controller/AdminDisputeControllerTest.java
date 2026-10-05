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
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDisputeController.class)
@Import({SecurityConfiguration.class, InternalApiKeyFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDenied.class})
@TestPropertySource(properties={"cors.allowed-origins=http://localhost:8080","internal.api.key=test-only-internal-key"})
class AdminDisputeControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ContractDisputeService service;
    @MockitoBean UserDetailsServiceCustomizer userDetails;
    @MockitoBean JwtService jwt;
    UUID dispute = UUID.randomUUID(), actor = UUID.randomUUID();
    UserPrincipal principal(boolean admin) {
        Role role = new Role();
        role.setName(admin ? "ROLE_ADMIN" : "ROLE_USER");
        return new UserPrincipal(actor, "admin@example.test", "hash", Set.of(role), true);
    }
    String path() { return "/api/v1/admin/disputes"; }
    @Test void unauthenticatedAndParticipantCannotUseAdminEndpoints() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(get(path()).with(user(principal(false)))).andExpect(status().isForbidden());
        mvc.perform(get(path()+"/"+dispute).with(user(principal(false)))).andExpect(status().isForbidden());
        mvc.perform(post(path()+"/"+dispute+"/claim").with(user(principal(false)))).andExpect(status().isForbidden());
        mvc.perform(post(path()+"/"+dispute+"/resolve").with(user(principal(false)))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void adminRoutesRequireRealRoleAndPassAuthenticatedActor() throws Exception {
        mvc.perform(get(path()).with(user(principal(true)))).andExpect(status().isOk());
        mvc.perform(get(path()+"/"+dispute).with(user(principal(true)))).andExpect(status().isOk());
        mvc.perform(post(path()+"/"+dispute+"/claim").with(user(principal(true)))).andExpect(status().isOk());
        mvc.perform(post(path()+"/"+dispute+"/resolve").with(user(principal(true)))
                .header("Idempotency-Key", "decision-1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"REFUND_TO_CLIENT\",\"reason\":\"Evidence reviewed\"}"))
                .andExpect(status().isOk());
        verify(service).claim(actor, dispute);
        verify(service).resolve(eq(actor), eq(dispute), eq("decision-1"), any());
    }
}
