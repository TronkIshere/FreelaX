package com.payment.backend.service.impl;

import com.payment.backend.service.EmailService;
import com.payment.backend.service.OTPService;
import com.payment.backend.service.RedisService;
import com.payment.backend.util.OTPGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OTPServiceImpl implements OTPService {

    private static final long OTP_TTL_MINUTES = 5;

    private final RedisService redisService;
    private final EmailService emailService;

    @Override
    public void sendOTP(String email) {
        String otp = OTPGenerator.generateOTP();
        redisService.save(otpKey(email), otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);
        emailService.sendOtpEmail(email, otp);
    }

    @Override
    public boolean verifyOTP(String email, String otp) {
        String stored = redisService.get(otpKey(email));
        boolean valid = stored != null && Objects.equals(stored, otp);
        if (valid) {
            redisService.delete(otpKey(email));
        }
        return valid;
    }

    private String otpKey(String email) {
        return "otp:" + email;
    }
}
