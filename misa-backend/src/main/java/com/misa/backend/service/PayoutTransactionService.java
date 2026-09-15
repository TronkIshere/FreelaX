package com.misa.backend.service;

import com.misa.backend.dto.request.misa.CreatePayoutTransactionRequest;
import com.misa.backend.dto.response.misa.PayoutTransactionResponse;

import java.util.UUID;

public interface PayoutTransactionService {

    PayoutTransactionResponse record(UUID taxpayerId, CreatePayoutTransactionRequest request);

    PayoutTransactionResponse getById(UUID payoutTransactionId);
}
