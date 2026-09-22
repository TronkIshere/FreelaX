package com.marketplace.backend.service.impl;

import com.marketplace.backend.service.OTPService;
import com.marketplace.backend.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OTPServiceImpl implements OTPService {

    private static final String KEY_PREFIX = "otp:";
    private static final long TTL_MINUTES = 5;

    private final RedisService redisService;

    @Override
    public void sendOTP(String email) {
        String otp = generateOtp();
        redisService.save(KEY_PREFIX + email, otp, TTL_MINUTES, TimeUnit.MINUTES);
        log.info("OTP for {} is {} (valid {} minutes)", email, otp, TTL_MINUTES);
    }

    @Override
    public boolean verifyOTP(String email, String otp) {
        String stored = redisService.get(KEY_PREFIX + email);
        boolean valid = stored != null && stored.equals(otp);
        if (valid) {
            redisService.delete(KEY_PREFIX + email);
        }
        return valid;
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
