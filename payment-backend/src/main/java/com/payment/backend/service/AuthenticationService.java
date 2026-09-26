package com.payment.backend.service;

import com.payment.backend.dto.request.auth.LogoutRequest;
import com.payment.backend.dto.request.auth.RegisterRequest;
import com.payment.backend.dto.request.auth.ResetPasswordRequest;
import com.payment.backend.dto.request.auth.SignInRequest;
import com.payment.backend.dto.request.auth.VerifyForgotPasswordOtpRequest;
import com.payment.backend.dto.response.auth.RefreshTokenResponse;
import com.payment.backend.dto.response.auth.SignInResponse;
import com.payment.backend.dto.response.auth.UserResponse;
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
