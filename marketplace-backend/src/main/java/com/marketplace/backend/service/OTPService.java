package com.marketplace.backend.service;

public interface OTPService {

    void sendOTP(String email);

    boolean verifyOTP(String email, String otp);
}
