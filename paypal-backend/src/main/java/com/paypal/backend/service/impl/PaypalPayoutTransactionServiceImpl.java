package com.paypal.backend.service.impl;

import com.paypal.backend.dto.request.paypal.RecordPaypalPayoutRequest;
import com.paypal.backend.dto.request.paypal.WithdrawPaypalPayoutRequest;
import com.paypal.backend.dto.response.paypal.PaypalFeeBreakdown;
import com.paypal.backend.dto.response.paypal.PaypalPayoutTransactionResponse;
import com.paypal.backend.entity.PaypalPayee;
import com.paypal.backend.entity.PaypalPayoutTransaction;
import com.paypal.backend.entity.PaypalTransactionStatus;
import com.paypal.backend.exception.ApplicationException;
import com.paypal.backend.exception.ErrorCode;
import com.paypal.backend.repository.PaypalPayeeRepository;
import com.paypal.backend.repository.PaypalPayoutTransactionRepository;
import com.paypal.backend.service.PaypalFeeCalculatorService;
import com.paypal.backend.service.PaypalPayoutTransactionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaypalPayoutTransactionServiceImpl implements PaypalPayoutTransactionService {

    PaypalPayoutTransactionRepository paypalPayoutTransactionRepository;
    PaypalPayeeRepository paypalPayeeRepository;
    PaypalFeeCalculatorService paypalFeeCalculatorService;

    @Override
    @Transactional
    public PaypalPayoutTransactionResponse record(UUID userId, UUID payeeId, RecordPaypalPayoutRequest request) {
        if (paypalPayoutTransactionRepository.existsByPlatformPayoutId(request.getPlatformPayoutId())) {
            throw new ApplicationException(ErrorCode.DUPLICATE_PLATFORM_PAYOUT_ID, request.getPlatformPayoutId());
        }

        PaypalPayee payee = paypalPayeeRepository.findById(payeeId)
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYEE_NOT_FOUND, payeeId));

        PaypalFeeCalculatorService.FeeCalculationResult feeResult =
                paypalFeeCalculatorService.calculate(request.getGrossAmountUsd(), request.getMidMarketRate());

        PaypalPayoutTransaction transaction = new PaypalPayoutTransaction();
        transaction.setPlatformPayoutId(request.getPlatformPayoutId());
        transaction.setPayee(payee);
        transaction.setSenderReference(request.getSenderReference());
        transaction.setDescription(request.getDescription());
        transaction.setGrossAmountUsd(request.getGrossAmountUsd());
        transaction.setMidMarketRate(request.getMidMarketRate());
        transaction.setCommercialFeeUsd(feeResult.commercialFeeUsd());
        transaction.setFxSpreadCostUsd(feeResult.fxSpreadCostUsd());
        transaction.setNetUsdAfterFees(feeResult.netUsdAfterFees());
        transaction.setNetVnd(feeResult.netVnd());
        transaction.setEffectiveFeeRatePercent(feeResult.effectiveFeeRatePercent());
        transaction.setStatus(PaypalTransactionStatus.RECEIVED);
        transaction.setPaymentDate(request.getPaymentDate());

        paypalPayoutTransactionRepository.save(transaction);

        return toResponse(transaction);
    }

    @Override
    public PaypalPayoutTransactionResponse getById(UUID userId, UUID payeeId, UUID transactionId) {
        PaypalPayoutTransaction transaction = getOwnedOrThrow(userId, payeeId, transactionId);
        return toResponse(transaction);
    }

    @Override
    @Transactional
    public PaypalPayoutTransactionResponse withdraw(UUID userId, UUID payeeId, UUID transactionId,
                                                    WithdrawPaypalPayoutRequest request) {
        PaypalPayoutTransaction transaction = getOwnedOrThrow(userId, payeeId, transactionId);

        if (transaction.getStatus() != PaypalTransactionStatus.RECEIVED) {
            throw new ApplicationException(ErrorCode.INVALID_TRANSACTION_STATUS);
        }

        transaction.setStatus(PaypalTransactionStatus.WITHDRAWN);
        transaction.setWithdrawnAt(LocalDateTime.now());

        paypalPayoutTransactionRepository.save(transaction);

        return toResponse(transaction);
    }

    private PaypalPayoutTransaction getOwnedOrThrow(UUID userId, UUID payeeId, UUID transactionId) {
        PaypalPayoutTransaction transaction = paypalPayoutTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYOUT_NOT_FOUND, transactionId));

        PaypalPayee payee = transaction.getPayee();
        boolean matchesPathPayee = payee.getId().equals(payeeId);
        boolean belongsToCaller = payee.getUserId().equals(userId);
        if (!matchesPathPayee || !belongsToCaller) {
            throw new ApplicationException(ErrorCode.PAYOUT_NOT_FOUND, transactionId);
        }

        return transaction;
    }

    @Override
    public List<PaypalPayoutTransactionResponse> list(UUID userId, UUID payeeId) {
        PaypalPayee payee = paypalPayeeRepository.findById(payeeId)
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYEE_NOT_FOUND, payeeId));

        return paypalPayoutTransactionRepository.findByPayeeId(payee.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PaypalPayoutTransactionResponse toResponse(PaypalPayoutTransaction transaction) {
        return PaypalPayoutTransactionResponse.builder()
                .id(transaction.getId())
                .platformPayoutId(transaction.getPlatformPayoutId())
                .payeeId(transaction.getPayee().getId())
                .status(transaction.getStatus().name())
                .grossAmountUsd(transaction.getGrossAmountUsd())
                .midMarketRate(transaction.getMidMarketRate())
                .feeBreakdown(PaypalFeeBreakdown.builder()
                        .commercialFeeUsd(transaction.getCommercialFeeUsd())
                        .fxSpreadCostUsd(transaction.getFxSpreadCostUsd())
                        .netUsdAfterFees(transaction.getNetUsdAfterFees())
                        .effectiveFeeRatePercent(transaction.getEffectiveFeeRatePercent())
                        .build())
                .netVnd(transaction.getNetVnd())
                .paymentDate(transaction.getPaymentDate())
                .withdrawnAt(transaction.getWithdrawnAt())
                .build();
    }
}