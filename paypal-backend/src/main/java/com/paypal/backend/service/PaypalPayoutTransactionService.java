package com.paypal.backend.service;

import com.paypal.backend.dto.request.paypal.RecordPaypalPayoutRequest;
import com.paypal.backend.dto.request.paypal.WithdrawPaypalPayoutRequest;
import com.paypal.backend.dto.response.paypal.PaypalPayoutTransactionResponse;

import java.util.UUID;

public interface PaypalPayoutTransactionService {

    PaypalPayoutTransactionResponse record(UUID payeeId, RecordPaypalPayoutRequest request);

    PaypalPayoutTransactionResponse getById(UUID transactionId);

    PaypalPayoutTransactionResponse withdraw(UUID transactionId, WithdrawPaypalPayoutRequest request);
}
