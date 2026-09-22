package com.paypal.backend.controller;

import com.paypal.backend.dto.request.paypal.CreateCheckoutOrderRequest;
import com.paypal.backend.dto.response.common.ResponseAPI;
import com.paypal.backend.dto.response.paypal.PaypalCheckoutOrderResponse;
import com.paypal.backend.service.PaypalCheckoutOrderService;
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
@RequestMapping("/internal/paypal/checkout/orders")
@RequiredArgsConstructor
public class PaypalCheckoutOrderController {

    private final PaypalCheckoutOrderService paypalCheckoutOrderService;

    @PostMapping
    public ResponseAPI<PaypalCheckoutOrderResponse> create(@Valid @RequestBody CreateCheckoutOrderRequest request) {
        return ResponseAPI.<PaypalCheckoutOrderResponse>builder()
                .code(200)
                .data(paypalCheckoutOrderService.create(request))
                .build();
    }

    @PostMapping("/{orderId}/capture")
    public ResponseAPI<PaypalCheckoutOrderResponse> capture(@PathVariable UUID orderId) {
        return ResponseAPI.<PaypalCheckoutOrderResponse>builder()
                .code(200)
                .data(paypalCheckoutOrderService.capture(orderId))
                .build();
    }

    @GetMapping("/{orderId}")
    public ResponseAPI<PaypalCheckoutOrderResponse> getById(@PathVariable UUID orderId) {
        return ResponseAPI.<PaypalCheckoutOrderResponse>builder()
                .code(200)
                .data(paypalCheckoutOrderService.getById(orderId))
                .build();
    }
}
