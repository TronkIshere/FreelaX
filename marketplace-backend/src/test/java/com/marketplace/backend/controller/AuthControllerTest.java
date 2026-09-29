package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.auth.RefreshTokenRequest;
import com.marketplace.backend.dto.response.auth.RefreshTokenResponse;
import com.marketplace.backend.dto.response.auth.UserResponse;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.entity.UserType;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    AuthenticationService authenticationService;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    AuthController authController;

    @Test
    void getCurrentUserReturnsClientUserType() {
        assertCurrentUserType(UserType.CLIENT);
    }

    @Test
    void getCurrentUserReturnsFreelancerUserType() {
        assertCurrentUserType(UserType.FREELANCER);
    }

    @Test
    void refreshTokenUsesJsonBodyWhenPresent() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("body-refresh-token");

        RefreshTokenResponse refreshed = RefreshTokenResponse.builder()
                .accessToken("new-access-token")
                .build();

        when(authenticationService.refreshToken("body-refresh-token"))
                .thenReturn(refreshed);

        ResponseAPI<RefreshTokenResponse> response =
                authController.refreshToken(request, "cookie-refresh-token");

        assertEquals("new-access-token", response.getData().getAccessToken());
        verify(authenticationService).refreshToken("body-refresh-token");
    }

    @Test
    void refreshTokenFallsBackToCookieForExistingClients() throws Exception {
        RefreshTokenResponse refreshed = RefreshTokenResponse.builder()
                .accessToken("cookie-access-token")
                .build();

        when(authenticationService.refreshToken("cookie-refresh-token"))
                .thenReturn(refreshed);

        ResponseAPI<RefreshTokenResponse> response =
                authController.refreshToken(null, "cookie-refresh-token");

        assertEquals("cookie-access-token", response.getData().getAccessToken());
        verify(authenticationService).refreshToken("cookie-refresh-token");
    }

    @Test
    void refreshTokenRejectsRequestWhenNoTokenExists() {
        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> authController.refreshToken(null, null)
        );

        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, exception.getErrorCode());
    }

    private void assertCurrentUserType(UserType userType) {
        UUID userId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setEmail(userType.name().toLowerCase() + "@example.com");
        user.setDisplayName(userType.name());
        user.setUserType(userType);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserPrincipal principal = new UserPrincipal(
                userId,
                user.getEmail(),
                "password",
                Set.of(),
                true
        );

        ResponseAPI<UserResponse> response = authController.getCurrentUser(principal);

        assertEquals(userType, response.getData().getUserType());
    }
}
