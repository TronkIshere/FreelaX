package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.InternalApiKeyFilter;
import com.marketplace.backend.configuration.SecurityConfiguration;
import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.configuration.jwt.JwtAccessDenied;
import com.marketplace.backend.configuration.jwt.JwtAuthenticationEntryPoint;
import com.marketplace.backend.dto.response.auth.RefreshTokenResponse;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.UserType;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.AuthenticationService;
import com.marketplace.backend.service.JwtService;
import com.marketplace.backend.service.UserDetailsServiceCustomizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfiguration.class, InternalApiKeyFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDenied.class})
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:8080",
        "internal.api.key=test-internal-key"
})
class AuthMeSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthenticationService authenticationService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    UserDetailsServiceCustomizer userDetailsService;

    @MockitoBean
    JwtService jwtService;

    @Test
    void unauthenticatedMeReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(2000));
    }

    @ParameterizedTest
    @EnumSource(value = UserType.class, names = {"CLIENT", "FREELANCER"})
    void authenticatedMeReturnsUserType(UserType userType) throws Exception {
        UUID id = UUID.randomUUID();
        User account = new User();
        account.setId(id);
        account.setEmail(userType.name().toLowerCase() + "@example.com");
        account.setDisplayName(userType.name());
        account.setUserType(userType);
        when(userRepository.findById(id)).thenReturn(Optional.of(account));
        UserPrincipal principal = new UserPrincipal(id, account.getEmail(), "hash", Set.of(), true);

        mockMvc.perform(get("/api/v1/auth/me").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userType").value(userType.name()));
    }

    @Test
    void signInAndRegisterRemainPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/sign-in")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshTokenRemainsPublic() throws Exception {
        when(authenticationService.refreshToken("valid-refresh"))
                .thenReturn(RefreshTokenResponse.builder().accessToken("new-access").build());
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"valid-refresh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"));
    }
}
