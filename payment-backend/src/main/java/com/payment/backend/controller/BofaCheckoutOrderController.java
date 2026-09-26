package com.payment.backend.controller;

import com.payment.backend.dto.request.bofa.CreateCheckoutOrderRequest;
import com.payment.backend.dto.response.common.ResponseAPI;
import com.payment.backend.dto.response.bofa.BofaCheckoutOrderResponse;
import com.payment.backend.service.BofaCheckoutOrderService;
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
@RequestMapping("/internal/bofa/checkout/orders")
@RequiredArgsConstructor
public class BofaCheckoutOrderController {

    private final BofaCheckoutOrderService bofaCheckoutOrderService;

    @PostMapping
    public ResponseAPI<BofaCheckoutOrderResponse> create(@Valid @RequestBody CreateCheckoutOrderRequest request) {
        return ResponseAPI.<BofaCheckoutOrderResponse>builder()
                .code(200)
                .data(bofaCheckoutOrderService.create(request))
                .build();
    }

    @PostMapping("/{orderId}/capture")
    public ResponseAPI<BofaCheckoutOrderResponse> capture(@PathVariable UUID orderId) {
        return ResponseAPI.<BofaCheckoutOrderResponse>builder()
                .code(200)
                .data(bofaCheckoutOrderService.capture(orderId))
                .build();
    }

    @GetMapping("/{orderId}")
    public ResponseAPI<BofaCheckoutOrderResponse> getById(@PathVariable UUID orderId) {
        return ResponseAPI.<BofaCheckoutOrderResponse>builder()
                .code(200)
                .data(bofaCheckoutOrderService.getById(orderId))
                .build();
    }
}
