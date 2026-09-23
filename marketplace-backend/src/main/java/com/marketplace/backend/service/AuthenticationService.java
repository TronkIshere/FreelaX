package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.auth.LogoutRequest;
import com.marketplace.backend.dto.request.auth.RegisterRequest;
import com.marketplace.backend.dto.request.auth.ResetPasswordRequest;
import com.marketplace.backend.dto.request.auth.SignInRequest;
import com.marketplace.backend.dto.request.auth.VerifyForgotPasswordOtpRequest;
import com.marketplace.backend.dto.response.auth.RefreshTokenResponse;
import com.marketplace.backend.dto.response.auth.SignInResponse;
import com.marketplace.backend.dto.response.auth.UserResponse;
import com.nimbusds.jose.JOSEException;
import jakarta.servlet.http.HttpServletResponse;

import java.text.ParseException;

public interface AuthenticationService {

    UserResponse registerUser(RegisterRequest request);

    SignInResponse signIn(SignInRequest request, HttpServletResponse response);

    RefreshTokenResponse refreshToken(String refreshToken) throws ParseException, JOSEException;

    void signOut(LogoutRequest request, HttpServletResponse response);

    void sendResetPasswordOTP(String email);

    String verifyOtp(VerifyForgotPasswordOtpRequest request);

    void resetPassword(ResetPasswordRequest request);
}
