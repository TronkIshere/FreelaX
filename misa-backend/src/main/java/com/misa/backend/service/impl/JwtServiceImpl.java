package com.misa.backend.service.impl;

import com.misa.backend.configuration.UserPrincipal;
import com.misa.backend.entity.Role;
import com.misa.backend.entity.User;
import com.misa.backend.exception.ApplicationException;
import com.misa.backend.exception.ErrorCode;
import com.misa.backend.repository.UserRepository;
import com.misa.backend.service.JwtService;
import com.misa.backend.service.RedisService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwtServiceImpl implements JwtService {

    private static final String ISSUER = "bromeclean-service";
    private static final String RESET_TOKEN_TYPE_CLAIM = "type";
    private static final String RESET_TOKEN_TYPE_VALUE = "RESET_PASSWORD";

    @Value("${security.jwt.secret}")
    String jwtSecret;

    @Value("${security.jwt.expiration-ms}")
    long jwtExpirationMs;

    final RedisService redisService;
    final UserRepository userRepository;

    @Override
    public String generateAccessToken(UserPrincipal user) {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getEmail())
                .issuer(ISSUER)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .jwtID(UUID.randomUUID().toString())
                .claim("roles", buildRoles(user))
                .build();

        return sign(claimsSet, JWSAlgorithm.HS512);
    }

    @Override
    public String generateRefreshToken(UserPrincipal user) {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getEmail())
                .issuer(ISSUER)
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plus(14, ChronoUnit.DAYS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .build();

        return sign(claimsSet, JWSAlgorithm.HS256);
    }

    @Override
    public String generateResetToken(String email) {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(email)
                .issuer(ISSUER)
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plus(5, ChronoUnit.MINUTES).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim(RESET_TOKEN_TYPE_CLAIM, RESET_TOKEN_TYPE_VALUE)
                .build();

        return sign(claimsSet, JWSAlgorithm.HS512);
    }

    private String sign(JWTClaimsSet claimsSet, JWSAlgorithm algorithm) {
        JWSHeader header = new JWSHeader(algorithm);
        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(jwtSecret.getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }

        return jwsObject.serialize();
    }

    private List<String> buildRoles(UserPrincipal userPrincipal) {
        if (userPrincipal == null || userPrincipal.getRoles() == null) {
            throw new ApplicationException(ErrorCode.USER_NOT_EXISTED);
        }

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ApplicationException(ErrorCode.USER_NOT_EXISTED));

        return user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());
    }

    @Override
    public boolean verificationToken(String token, UserPrincipal user) throws ParseException, JOSEException {
        SignedJWT signedJWT = SignedJWT.parse(token);

        String jwtId = signedJWT.getJWTClaimsSet().getJWTID();
        if (StringUtils.hasText(redisService.get(jwtId))) {
            throw new ApplicationException(ErrorCode.TOKEN_BLACKLISTED);
        }

        String email = signedJWT.getJWTClaimsSet().getSubject();
        Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();

        if (!Objects.equals(email, user.getEmail())) {
            log.warn("Email in token does not match authenticated user");
            throw new ApplicationException(ErrorCode.TOKEN_INVALID);
        }

        if (expiration.before(new Date())) {
            throw new ApplicationException(ErrorCode.TOKEN_EXPIRED);
        }

        return signedJWT.verify(new MACVerifier(jwtSecret.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String extractUserName(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet().getSubject();
        } catch (ParseException e) {
            throw new ApplicationException(ErrorCode.TOKEN_INVALID);
        }
    }

    @Override
    public long extractTokenExpired(String token) {
        try {
            long expirationTime = SignedJWT.parse(token)
                    .getJWTClaimsSet().getExpirationTime().getTime();
            return Math.max(expirationTime - System.currentTimeMillis(), 0);
        } catch (ParseException e) {
            throw new ApplicationException(ErrorCode.TOKEN_INVALID);
        }
    }

    @Override
    public boolean validateToken(String authToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(authToken);
            boolean verified = signedJWT.verify(new MACVerifier(jwtSecret.getBytes(StandardCharsets.UTF_8)));
            Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();
            return verified && expiration.after(new Date());
        } catch (Exception e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getEmailFromToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            String email = signedJWT.getJWTClaimsSet().getSubject();
            if (!StringUtils.hasText(email)) {
                throw new ApplicationException(ErrorCode.TOKEN_INVALID);
            }
            return email;
        } catch (ParseException e) {
            throw new ApplicationException(ErrorCode.TOKEN_INVALID);
        }
    }

    @Override
    public String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @Override
    public boolean validateResetToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();
            if (expiration.before(new Date())) {
                return false;
            }

            String type = (String) signedJWT.getJWTClaimsSet().getClaim(RESET_TOKEN_TYPE_CLAIM);
            if (!RESET_TOKEN_TYPE_VALUE.equals(type)) {
                return false;
            }

            return signedJWT.verify(new MACVerifier(jwtSecret.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.warn("Invalid reset token: {}", e.getMessage());
            return false;
        }
    }
}
