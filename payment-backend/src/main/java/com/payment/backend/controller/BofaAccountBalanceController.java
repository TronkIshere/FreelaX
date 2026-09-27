package com.payment.backend.controller;

import com.payment.backend.dto.response.common.ResponseAPI;
import com.payment.backend.dto.response.bofa.BofaAccountBalanceResponse;
import com.payment.backend.service.BofaAccountBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/BofA/balances")
@RequiredArgsConstructor
public class BofaAccountBalanceController {

    private final BofaAccountBalanceService bofaAccountBalanceService;

    @GetMapping("/{bankAccountNumber}")
    public ResponseAPI<BofaAccountBalanceResponse> get(@PathVariable String bankAccountNumber) {
        return ResponseAPI.<BofaAccountBalanceResponse>builder()
                .code(200)
                .data(bofaAccountBalanceService.getBalance(bankAccountNumber))
                .build();
    }
}