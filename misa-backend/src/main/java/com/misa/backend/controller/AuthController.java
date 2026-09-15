package com.misa.backend.controller;

import com.misa.backend.configuration.UserPrincipal;
import com.misa.backend.dto.request.auth.ForgotPasswordRequest;
import com.misa.backend.dto.request.auth.LogoutRequest;
import com.misa.backend.dto.request.auth.RegisterRequest;
import com.misa.backend.dto.request.auth.ResetPasswordRequest;
import com.misa.backend.dto.request.auth.SignInRequest;
import com.misa.backend.dto.request.auth.VerifyForgotPasswordOtpRequest;
import com.misa.backend.dto.response.auth.RefreshTokenResponse;
import com.misa.backend.dto.response.auth.SignInResponse;
import com.misa.backend.dto.response.auth.UserResponse;
import com.misa.backend.dto.response.common.ResponseAPI;
import com.misa.backend.entity.User;
import com.misa.backend.exception.ApplicationException;
import com.misa.backend.exception.ErrorCode;
import com.misa.backend.repository.UserRepository;
import com.misa.backend.service.AuthenticationService;
import com.nimbusds.jose.JOSEException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseAPI<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseAPI.<UserResponse>builder()
                .code(200)
                .message("Đăng ký thành công")
                .data(authenticationService.registerUser(request))
                .build();
    }

    @PostMapping("/sign-in")
    public ResponseAPI<SignInResponse> signIn(@Valid @RequestBody SignInRequest request,
                                              HttpServletResponse response) {
        return ResponseAPI.<SignInResponse>builder()
                .code(200)
                .message("Đăng nhập thành công")
                .data(authenticationService.signIn(request, response))
                .build();
    }

    @PostMapping("/refresh-token")
    public ResponseAPI<RefreshTokenResponse> refreshToken(
            @CookieValue(name = "refreshToken") String refreshToken) throws ParseException, JOSEException {
        return ResponseAPI.<RefreshTokenResponse>builder()
                .code(200)
                .data(authenticationService.refreshToken(refreshToken))
                .build();
    }

    @PostMapping("/sign-out")
    public ResponseAPI<Void> signOut(@Valid @RequestBody LogoutRequest request,
                                     HttpServletResponse response) {
        authenticationService.signOut(request, response);
        return ResponseAPI.<Void>builder()
                .code(200)
                .message("Đăng xuất thành công")
                .build();
    }

    @PostMapping("/forgot-password/send-otp")
    public ResponseAPI<Void> sendResetPasswordOtp(@Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.sendResetPasswordOTP(request.getEmail());
        return ResponseAPI.<Void>builder()
                .code(200)
                .message("Đã gửi mã OTP tới email")
                .build();
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseAPI<String> verifyResetPasswordOtp(@Valid @RequestBody VerifyForgotPasswordOtpRequest request) {
        return ResponseAPI.<String>builder()
                .code(200)
                .message("Xác thực OTP thành công")
                .data(authenticationService.verifyOtp(request))
                .build();
    }

    @PostMapping("/forgot-password/reset")
    public ResponseAPI<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request);
        return ResponseAPI.<Void>builder()
                .code(200)
                .message("Đặt lại mật khẩu thành công")
                .build();
    }

    @GetMapping("/me")
    public ResponseAPI<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.ACCOUNT_NOT_FOUND));

        return ResponseAPI.<UserResponse>builder()
                .code(200)
                .data(UserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .displayName(user.getDisplayName())
                        .build())
                .build();
    }
}