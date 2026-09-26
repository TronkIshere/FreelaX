package com.paypal.backend.controller;

import com.paypal.backend.dto.response.common.ResponseAPI;
import com.paypal.backend.dto.response.paypal.PaypalAccountBalanceResponse;
import com.paypal.backend.entity.PaypalAccountRole;
import com.paypal.backend.service.PaypalAccountBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/paypal/balances")
@RequiredArgsConstructor
public class PaypalAccountBalanceController {

    private final PaypalAccountBalanceService paypalAccountBalanceService;

    @GetMapping("/{accountId}")
    public ResponseAPI<PaypalAccountBalanceResponse> get(@PathVariable UUID accountId,
                                                         @RequestParam PaypalAccountRole role) {
        return ResponseAPI.<PaypalAccountBalanceResponse>builder()
                .code(200)
                .data(paypalAccountBalanceService.getBalance(accountId, role))
                .build();
    }
}