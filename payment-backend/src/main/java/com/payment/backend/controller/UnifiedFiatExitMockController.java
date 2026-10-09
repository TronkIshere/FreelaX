package com.payment.backend.controller;

import com.payment.backend.dto.response.common.ResponseAPI;
import com.payment.backend.service.UnifiedFiatExitMockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/unified-mock/fiat-exits")
public class UnifiedFiatExitMockController {
    private final UnifiedFiatExitMockService service;

    @PostMapping
    public ResponseAPI<UnifiedFiatExitMockService.ExitView> request(
            @RequestBody UnifiedFiatExitMockService.ExitRequest input) {
        return ok(service.request(input));
    }

    @GetMapping("/{paymentFlowId}")
    public ResponseAPI<UnifiedFiatExitMockService.ExitView> get(@PathVariable UUID paymentFlowId) {
        return ok(service.get(paymentFlowId));
    }

    @GetMapping("/{paymentFlowId}/statement")
    public ResponseAPI<UnifiedFiatExitMockService.StatementView> statement(
            @PathVariable UUID paymentFlowId) {
        return ok(service.statement(paymentFlowId));
    }

    private <T> ResponseAPI<T> ok(T data) {
        return ResponseAPI.<T>builder().code(200).data(data).build();
    }
}
