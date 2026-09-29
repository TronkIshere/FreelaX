package com.misa.backend.service.impl;

import com.misa.backend.dto.request.misa.CreatePayoutTransactionRequest;
import com.misa.backend.dto.response.misa.PayoutTransactionResponse;
import com.misa.backend.entity.PayoutTransaction;
import com.misa.backend.entity.Taxpayer;
import com.misa.backend.exception.ApplicationException;
import com.misa.backend.exception.ErrorCode;
import com.misa.backend.repository.PayoutTransactionRepository;
import com.misa.backend.repository.TaxpayerRepository;
import com.misa.backend.service.PayoutTransactionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PayoutTransactionServiceImpl implements PayoutTransactionService {

    PayoutTransactionRepository payoutTransactionRepository;
    TaxpayerRepository taxpayerRepository;

    @Override
    @Transactional
    public PayoutTransactionResponse record(UUID taxpayerId, CreatePayoutTransactionRequest request) {
        PayoutTransaction existing = payoutTransactionRepository
                .findByPlatformPayoutId(request.getPlatformPayoutId()).orElse(null);
        if (existing != null) {
            // A repeated platform payout ID recovers an earlier successful POST, including
            // a response lost before Marketplace could persist the MISA transaction ID.
            if (!existing.getTaxpayer().getId().equals(taxpayerId)
                    || !Objects.equals(existing.getTransactionHash(), request.getTransactionHash())
                    || !Objects.equals(existing.getBlockchain(), request.getBlockchain())
                    || existing.getAmountUsdc().compareTo(request.getAmountUsdc()) != 0
                    || existing.getExchangeRate().compareTo(request.getExchangeRate()) != 0) {
                throw new ApplicationException(ErrorCode.DATA_ALREADY_EXISTS, request.getPlatformPayoutId());
            }
            return toResponse(existing);
        }

        Taxpayer taxpayer = taxpayerRepository.findById(taxpayerId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.TAXPAYER_NOT_FOUND, taxpayerId));

        PayoutTransaction transaction = new PayoutTransaction();
        transaction.setPlatformPayoutId(request.getPlatformPayoutId());
        transaction.setTaxpayer(taxpayer);
        transaction.setBlockchain(request.getBlockchain());
        transaction.setTransactionHash(request.getTransactionHash());
        transaction.setDescription(request.getDescription());
        transaction.setAmountUsdc(request.getAmountUsdc());
        transaction.setExchangeRate(request.getExchangeRate());
        transaction.setAmountVndGross(request.getAmountUsdc().multiply(request.getExchangeRate()));
        transaction.setPaymentDate(request.getPaymentDate());

        payoutTransactionRepository.save(transaction);

        return toResponse(transaction);
    }

    @Override
    public PayoutTransactionResponse getById(UUID payoutTransactionId) {
        PayoutTransaction transaction = payoutTransactionRepository.findById(payoutTransactionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.PAYOUT_NOT_FOUND, payoutTransactionId));
        return toResponse(transaction);
    }

    private PayoutTransactionResponse toResponse(PayoutTransaction transaction) {
        return PayoutTransactionResponse.builder()
                .id(transaction.getId())
                .platformPayoutId(transaction.getPlatformPayoutId())
                .taxpayerId(transaction.getTaxpayer().getId())
                .blockchain(transaction.getBlockchain())
                .transactionHash(transaction.getTransactionHash())
                .description(transaction.getDescription())
                .amountUsdc(transaction.getAmountUsdc())
                .exchangeRate(transaction.getExchangeRate())
                .amountVndGross(transaction.getAmountVndGross())
                .paymentDate(transaction.getPaymentDate())
                .build();
    }
}
