package com.marketplace.backend.service.impl;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.request.auth.ForgotPasswordRequest;
import com.marketplace.backend.dto.request.auth.LogoutRequest;
import com.marketplace.backend.dto.request.auth.RegisterRequest;
import com.marketplace.backend.dto.request.auth.ResetPasswordRequest;
import com.marketplace.backend.dto.request.auth.SignInRequest;
import com.marketplace.backend.dto.request.auth.VerifyForgotPasswordOtpRequest;
import com.marketplace.backend.dto.response.auth.RefreshTokenResponse;
import com.marketplace.backend.dto.response.auth.SignInResponse;
import com.marketplace.backend.dto.response.auth.SignInStatus;
import com.marketplace.backend.dto.response.auth.UserResponse;
import com.marketplace.backend.entity.AuthProvider;
import com.marketplace.backend.entity.Role;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.RoleRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.JwtService;
import com.marketplace.backend.service.OTPService;
import com.marketplace.backend.service.RedisService;
import com.marketplace.backend.service.UserDetailsServiceCustomizer;
import com.marketplace.backend.service.AuthenticationService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationServiceImpl implements AuthenticationService {

    UserDetailsServiceCustomizer userDetailsServiceCustomizer;
    AuthenticationManager authenticationManager;
    PasswordEncoder passwordEncoder;
    UserRepository userRepository;
    RoleRepository roleRepository;
    RedisService redisService;
    JwtService jwtService;
    OTPService otpService;

    @Override
    @Transactional
    public UserResponse registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApplicationException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, (Object) "ROLE_USER"));

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEnabled(true);
        user.setRoles(Set.of(userRole));
        userRepository.save(user);


        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .build();
    }

    @Override
    public SignInResponse signIn(SignInRequest request, HttpServletResponse response) {
        UserPrincipal userPrincipal = authenticateAndGetUserPrincipal(request.getEmail(), request.getPassword());

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_EXISTED));

        return generateTokenResponse(userPrincipal, user, response);
    }

    private UserPrincipal authenticateAndGetUserPrincipal(String email, String password) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_CREDENTIALS));

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
        } catch (AuthenticationException e) {
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
        }

        return (UserPrincipal) authentication.getPrincipal();
    }

    private SignInResponse generateTokenResponse(UserPrincipal userPrincipal, User user, HttpServletResponse response) {
        String accessToken = jwtService.generateAccessToken(userPrincipal);
        String refreshToken = jwtService.generateRefreshToken(userPrincipal);

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        response.addCookie(buildRefreshTokenCookie(refreshToken, 14 * 24 * 60 * 60));

        return SignInResponse.builder()
                .status(SignInStatus.SUCCESS)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .build();
    }

    private Cookie buildRefreshTokenCookie(String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie("refreshToken", value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        return cookie;
    }

    @Override
    public RefreshTokenResponse refreshToken(String refreshToken) throws ParseException, JOSEException {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApplicationException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String email = jwtService.extractUserName(refreshToken);
        UserDetails userDetails = userDetailsServiceCustomizer.loadUserByUsername(email);
        UserPrincipal userPrincipal = (UserPrincipal) userDetails;

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_EXISTED));

        validateStoredRefreshToken(user, refreshToken);

        String accessToken = jwtService.generateAccessToken(UserPrincipal.create(user));

        return RefreshTokenResponse.builder()
                .accessToken(accessToken)
                .userId(user.getId())
                .build();
    }

    private void validateStoredRefreshToken(User user, String refreshToken) throws ParseException, JOSEException {
        if (!StringUtils.hasText(user.getRefreshToken())
                || !Objects.equals(refreshToken, user.getRefreshToken())) {
            throw new ApplicationException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        if (!jwtService.verificationToken(refreshToken, UserPrincipal.create(user))) {
            throw new ApplicationException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    @Override
    public void signOut(LogoutRequest request, HttpServletResponse response) {
        String email = jwtService.extractUserName(request.getAccessToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_EXISTED));

        long remainingMs = jwtService.extractTokenExpired(request.getAccessToken());
        if (remainingMs > 0) {
            try {
                String jwtId = SignedJWT.parse(request.getAccessToken()).getJWTClaimsSet().getJWTID();
                redisService.save(jwtId, request.getAccessToken(), remainingMs, TimeUnit.MILLISECONDS);
            } catch (ParseException e) {
                throw new ApplicationException(ErrorCode.SIGN_OUT_FAILED);
            }
        }

        user.setRefreshToken(null);
        userRepository.save(user);

        response.addCookie(buildRefreshTokenCookie("", 0));
    }

    @Override
    public void sendResetPasswordOTP(String email) {
        if (!userRepository.existsByEmail(email)) {
            throw new ApplicationException(ErrorCode.EMAIL_NOT_FOUND);
        }
        otpService.sendOTP(email);
    }

    @Override
    public String verifyOtp(VerifyForgotPasswordOtpRequest request) {
        if (!otpService.verifyOTP(request.getEmail(), request.getOtp())) {
            throw new ApplicationException(ErrorCode.INVALID_OTP);
        }
        return jwtService.generateResetToken(request.getEmail());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!jwtService.validateResetToken(request.getResetToken())) {
            throw new ApplicationException(ErrorCode.INVALID_OTP);
        }

        String email = jwtService.getEmailFromToken(request.getResetToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApplicationException(ErrorCode.EMAIL_NOT_FOUND));

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ApplicationException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
