package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.InternalApiKeyFilter;
import com.marketplace.backend.configuration.SecurityConfiguration;
import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.configuration.jwt.JwtAccessDenied;
import com.marketplace.backend.configuration.jwt.JwtAuthenticationEntryPoint;
import com.marketplace.backend.dto.response.profile.ProfileResponse;
import com.marketplace.backend.entity.UserType;
import com.marketplace.backend.service.JwtService;
import com.marketplace.backend.service.ProfileService;
import com.marketplace.backend.service.UserDetailsServiceCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfiguration.class, InternalApiKeyFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDenied.class})
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:8080", "internal.api.key=test-internal-key"
})
class ProfileControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfileService service;
    @MockitoBean UserDetailsServiceCustomizer userDetailsService;
    @MockitoBean JwtService jwtService;

    @Test
    void profileRoutesRequireLogin() throws Exception {
        mvc.perform(get("/api/v1/profiles/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/profiles/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void publicRouteSerializesOnlySafeProfileFields() throws Exception {
        UUID target = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        var response = new ProfileResponse(target, UserType.FREELANCER, "Freelancer", null,
                0, null, "Engineer", null, "VN", List.of(), List.of(), null, null,
                null, null, new ProfileResponse.Verification("UNVERIFIED", "UNVERIFIED", "UNVERIFIED", "NOT_CONFIGURED"),
                new ProfileResponse.Reputation(0, null, 0, 0, null, null, null, null, Instant.now()));
        when(service.publicProfile(eq(target))).thenReturn(response);
        UserPrincipal principal = new UserPrincipal(viewer, "viewer@example.test", "hash", Set.of(), true);
        mvc.perform(get("/api/v1/profiles/" + target).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").value("Engineer"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.reputation.averageRating").value(org.hamcrest.Matchers.nullValue()));
    }
}
