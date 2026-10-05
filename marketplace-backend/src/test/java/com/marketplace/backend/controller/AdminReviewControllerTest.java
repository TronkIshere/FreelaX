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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminReviewController.class)
@Import({SecurityConfiguration.class, InternalApiKeyFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDenied.class})
@TestPropertySource(properties={"cors.allowed-origins=http://localhost:8080","internal.api.key=test-only-internal-key"})
class AdminReviewControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ContractReviewService service;
    @MockitoBean UserDetailsServiceCustomizer userDetails;
    @MockitoBean JwtService jwt;
    UUID review = UUID.randomUUID(), actor = UUID.randomUUID();
    UserPrincipal principal(boolean admin) {
        Role role = new Role(); role.setName(admin ? "ROLE_ADMIN" : "ROLE_USER");
        return new UserPrincipal(actor, "reviewer@example.test", "hash", Set.of(role), true);
    }

    @Test void participantCannotModerateOrReadReports() throws Exception {
        mvc.perform(get("/api/v1/admin/reviews/reported")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/reviews/reported").with(user(principal(false))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reviews/" + review).with(user(principal(false))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/reviews/" + review + "/moderation")
                .with(user(principal(false))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"INVALIDATE\",\"reason\":\"Abuse\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void adminRoleCanModerateWithAuthenticatedIdentity() throws Exception {
        mvc.perform(get("/api/v1/admin/reviews/reported").with(user(principal(true))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/reviews/" + review).with(user(principal(true))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/reviews/" + review + "/moderation")
                .with(user(principal(true))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"HIDE_CONTENT\",\"reason\":\"Abuse\"}"))
                .andExpect(status().isOk());
        verify(service).moderate(eq(actor), eq(review), any());
        verify(service).adminDetail(review);
    }
}
