package com.payment.backend.controller;

import com.payment.backend.dto.response.common.ResponseAPI;
import com.payment.backend.service.UnifiedUsdOrderMockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/unified-mock/usd-orders")
public class UnifiedUsdOrderMockController {
    private final UnifiedUsdOrderMockService service;

    @PostMapping
    public ResponseAPI<UnifiedUsdOrderMockService.OrderView> open(
            @RequestBody UnifiedUsdOrderMockService.OpenRequest request) {
        return ok(service.open(request));
    }

    @GetMapping("/{paymentFlowId}")
    public ResponseAPI<UnifiedUsdOrderMockService.OrderView> get(@PathVariable UUID paymentFlowId) {
        return ok(service.get(paymentFlowId));
    }

    @PostMapping("/{paymentFlowId}/submit")
    public ResponseAPI<UnifiedUsdOrderMockService.OrderView> submit(@PathVariable UUID paymentFlowId,
            @RequestBody java.util.Map<String, String> body) {
        return ok(service.submit(paymentFlowId, body.get("fundKey")));
    }

    @GetMapping("/{paymentFlowId}/statement")
    public ResponseAPI<UnifiedUsdOrderMockService.StatementView> statement(@PathVariable UUID paymentFlowId) {
        return ok(service.statement(paymentFlowId));
    }

    private <T> ResponseAPI<T> ok(T data) {
        return ResponseAPI.<T>builder().code(200).data(data).build();
    }
}
