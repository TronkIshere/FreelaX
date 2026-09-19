package com.paypal.backend.service;

import com.paypal.backend.dto.request.paypal.RecordPaypalPayoutRequest;
import com.paypal.backend.dto.request.paypal.WithdrawPaypalPayoutRequest;
import com.paypal.backend.dto.response.paypal.PaypalPayoutTransactionResponse;

import java.util.UUID;

public interface PaypalPayoutTransactionService {

    PaypalPayoutTransactionResponse record(UUID userId, UUID payeeId, RecordPaypalPayoutRequest request);

    PaypalPayoutTransactionResponse getById(UUID userId, UUID payeeId, UUID transactionId);

    PaypalPayoutTransactionResponse withdraw(UUID userId, UUID payeeId, UUID transactionId, WithdrawPaypalPayoutRequest request);
}