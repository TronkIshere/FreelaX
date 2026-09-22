package com.misa.backend.service;

import com.misa.backend.dto.request.auth.LogoutRequest;
import com.misa.backend.dto.request.auth.RegisterRequest;
import com.misa.backend.dto.request.auth.ResetPasswordRequest;
import com.misa.backend.dto.request.auth.SignInRequest;
import com.misa.backend.dto.request.auth.VerifyForgotPasswordOtpRequest;
import com.misa.backend.dto.response.auth.RefreshTokenResponse;
import com.misa.backend.dto.response.auth.SignInResponse;
import com.misa.backend.dto.response.auth.UserResponse;
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
