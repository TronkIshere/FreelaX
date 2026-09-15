package com.misa.backend.util;

import java.security.SecureRandom;
import java.time.Year;

public final class CertificateNumberGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOOKUP_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";

    private CertificateNumberGenerator() {
    }

    public static String generateSymbol() {
        int yy = Year.now().getValue() % 100;
        char first = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        char second = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        return String.format("CT%02d%c%c", yy, first, second);
    }

    public static String generateCertificateNumber(long sequence) {
        return String.format("%08d", sequence);
    }

    public static String generateLookupCode() {
        StringBuilder sb = new StringBuilder("LOOKUP-");
        for (int i = 0; i < 6; i++) {
            sb.append(LOOKUP_CHARS.charAt(RANDOM.nextInt(LOOKUP_CHARS.length())));
        }
        return sb.toString();
    }
}
