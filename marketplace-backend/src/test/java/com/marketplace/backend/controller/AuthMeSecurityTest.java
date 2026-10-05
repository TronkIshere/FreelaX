package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.InternalApiKeyFilter;
import com.marketplace.backend.configuration.SecurityConfiguration;
import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.configuration.jwt.JwtAccessDenied;
import com.marketplace.backend.configuration.jwt.JwtAuthenticationEntryPoint;
import com.marketplace.backend.dto.response.auth.RefreshTokenResponse;
import com.marketplace.backend.entity.Role;
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
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

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
        mockMvc.perform(get("/api/v1/auth/me").param("admin", "true")
                        .header("X-Authorities", "ROLE_ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(jsonPath("$.status").value(2000))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.error").value("Chưa đăng nhập"))
                .andExpect(result -> assertThat(new String(
                        result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                        .contains("Chưa đăng nhập").doesNotContain("?"));
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
                .andExpect(jsonPath("$.data.userType").value(userType.name()))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.email").value(account.getEmail()))
                .andExpect(jsonPath("$.data.displayName").value(account.getDisplayName()))
                .andExpect(jsonPath("$.data.authorities").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = UserType.class, names = {"CLIENT", "FREELANCER"})
    void jwtAuthenticatedAdminExposesStoredAuthoritiesWithoutChangingUserType(UserType userType) throws Exception {
        User account = authenticateThroughJwt(userType, Set.of(role("ROLE_USER"), role("ROLE_ADMIN")));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer opaque-test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(account.getId().toString()))
                .andExpect(jsonPath("$.data.email").value(account.getEmail()))
                .andExpect(jsonPath("$.data.displayName").value(account.getDisplayName()))
                .andExpect(jsonPath("$.data.userType").value(userType.name()))
                .andExpect(jsonPath("$.data.authorities[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.data.authorities[1]").value("ROLE_USER"))
                .andExpect(jsonPath("$.data.authorities.length()").value(2))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.roles").doesNotExist());
        verify(userDetailsService).loadUserByUsername(account.getEmail());
    }

    @Test
    void clientInputCannotForgeAuthoritiesForJwtAuthenticatedNonAdmin() throws Exception {
        authenticateThroughJwt(UserType.CLIENT, Set.of(role("ROLE_USER")));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer opaque-test-token")
                        .header("X-Authorities", "ROLE_ADMIN")
                        .param("admin", "true").param("authorities", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorities\":[\"ROLE_ADMIN\"],\"userType\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userType").value("CLIENT"))
                .andExpect(jsonPath("$.data.authorities[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.data.authorities.length()").value(1));
    }

    private User authenticateThroughJwt(UserType userType, Set<Role> roles) {
        User account = new User();
        account.setId(UUID.randomUUID());
        account.setEmail("account@example.com");
        account.setDisplayName("Trusted identity");
        account.setUserType(userType);
        account.setRoles(roles);
        account.setPassword("test-password-hash");
        account.setRefreshToken("test-refresh-token");
        when(userRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(jwtService.getJwtFromRequest(any())).thenReturn("opaque-test-token");
        when(jwtService.validateToken("opaque-test-token")).thenReturn(true);
        when(jwtService.extractUserName("opaque-test-token")).thenReturn(account.getEmail());
        when(userDetailsService.loadUserByUsername(account.getEmail()))
                .thenReturn(UserPrincipal.create(account));
        return account;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
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
