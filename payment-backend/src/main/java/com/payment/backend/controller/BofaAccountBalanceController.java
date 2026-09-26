package com.payment.backend.controller;

import com.payment.backend.dto.response.common.ResponseAPI;
import com.payment.backend.dto.response.bofa.BofaAccountBalanceResponse;
import com.payment.backend.entity.BofaAccountRole;
import com.payment.backend.service.BofaAccountBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/bofa/balances")
@RequiredArgsConstructor
public class BofaAccountBalanceController {

    private final BofaAccountBalanceService bofaAccountBalanceService;

    @GetMapping("/{accountId}")
    public ResponseAPI<BofaAccountBalanceResponse> get(@PathVariable UUID accountId,
                                                         @RequestParam BofaAccountRole role) {
        return ResponseAPI.<BofaAccountBalanceResponse>builder()
                .code(200)
                .data(bofaAccountBalanceService.getBalance(accountId, role))
                .build();
    }
}