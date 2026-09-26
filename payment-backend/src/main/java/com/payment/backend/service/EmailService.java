package com.payment.backend.service;

public interface EmailService {

    void sendOtpEmail(String toEmail, String otp);
}
