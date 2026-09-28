package com.marketplace.backend.provider.currency;

import com.marketplace.backend.entity.OnRampStatus;

import java.math.BigDecimal;

public record OnRampResult(
        OnRampStatus status,
        String transactionSignature,
        String clientUsdcAta,
        String receiptPda,
        BigDecimal amountUsdcReceived,
        String error
) {

    public static OnRampResult notStarted(String error) {
        return new OnRampResult(OnRampStatus.NOT_STARTED, null, null, null, null, error);
    }

    public static OnRampResult submitted(String signature, String clientUsdcAta, String receiptPda, String error) {
        return new OnRampResult(OnRampStatus.SUBMITTED, signature, clientUsdcAta, receiptPda, null, error);
    }

    public static OnRampResult confirmed(String signature, String clientUsdcAta, String receiptPda, BigDecimal amountUsdc) {
        return new OnRampResult(OnRampStatus.CONFIRMED, signature, clientUsdcAta, receiptPda, amountUsdc, null);
    }

    public static OnRampResult failed(String signature, String clientUsdcAta, String receiptPda, String error) {
        return new OnRampResult(OnRampStatus.FAILED, signature, clientUsdcAta, receiptPda, null, error);
    }
}