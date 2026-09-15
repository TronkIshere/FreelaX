package com.paypal.backend.util;

import java.security.SecureRandom;

public final class OTPGenerator {

    private OTPGenerator() {
    }

    public static String generateOTP() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}
