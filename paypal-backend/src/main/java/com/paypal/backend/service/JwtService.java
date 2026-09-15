package com.paypal.backend.service;

import com.paypal.backend.configuration.UserPrincipal;
import com.nimbusds.jose.JOSEException;
import jakarta.servlet.http.HttpServletRequest;

import java.text.ParseException;

public interface JwtService {

    String generateAccessToken(UserPrincipal user);

    String generateRefreshToken(UserPrincipal user);

    boolean verificationToken(String token, UserPrincipal user) throws ParseException, JOSEException;

    String extractUserName(String token);

    long extractTokenExpired(String accessToken);

    boolean validateToken(String authToken);

    String getEmailFromToken(String token);

    String getJwtFromRequest(HttpServletRequest request);

    String generateResetToken(String email);

    boolean validateResetToken(String token);
}
