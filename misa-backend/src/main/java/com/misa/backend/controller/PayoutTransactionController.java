package com.misa.backend.controller;

import com.misa.backend.dto.request.misa.CreatePayoutTransactionRequest;
import com.misa.backend.dto.response.common.ResponseAPI;
import com.misa.backend.dto.response.misa.PayoutTransactionResponse;
import com.misa.backend.service.PayoutTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/taxpayers/{taxpayerId}/payouts")
@RequiredArgsConstructor
public class PayoutTransactionController {

    private final PayoutTransactionService payoutTransactionService;

    @PostMapping
    public ResponseAPI<PayoutTransactionResponse> record(@PathVariable UUID taxpayerId,
                                                           @Valid @RequestBody CreatePayoutTransactionRequest request) {
        return ResponseAPI.<PayoutTransactionResponse>builder()
                .code(200)
                .message("Ghi nhận giao dịch payout thành công")
                .data(payoutTransactionService.record(taxpayerId, request))
                .build();
    }

    @GetMapping("/{payoutId}")
    public ResponseAPI<PayoutTransactionResponse> getById(@PathVariable UUID taxpayerId,
                                                            @PathVariable UUID payoutId) {
        return ResponseAPI.<PayoutTransactionResponse>builder()
                .code(200)
                .data(payoutTransactionService.getById(payoutId))
                .build();
    }
}
