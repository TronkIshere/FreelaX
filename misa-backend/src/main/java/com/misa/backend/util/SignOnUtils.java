package com.misa.backend.util;

import com.misa.backend.dto.response.auth.SignInStatus;

import java.util.UUID;

public final class SignOnUtils {

    private static final ThreadLocal<SignOnUser> CONTEXT = new ThreadLocal<>();

    private SignOnUtils() {
    }

    public static void set(SignOnUser signOnUser) {
        CONTEXT.set(signOnUser);
    }

    public static SignOnUser get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public record SignOnUser(
            UUID userId,
            String accessToken,
            String refreshToken,
            SignInStatus status,
            String extra,
            String email
    ) {
        public UUID getUserId() {
            return userId;
        }
    }
}
