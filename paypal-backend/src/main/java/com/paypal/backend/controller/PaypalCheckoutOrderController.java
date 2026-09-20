package com.paypal.backend.controller;

import com.paypal.backend.configuration.UserPrincipal;
import com.paypal.backend.dto.request.paypal.CreateCheckoutOrderRequest;
import com.paypal.backend.dto.response.common.ResponseAPI;
import com.paypal.backend.dto.response.paypal.PaypalCheckoutOrderResponse;
import com.paypal.backend.service.PaypalCheckoutOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/paypal/checkout/orders")
@RequiredArgsConstructor
public class PaypalCheckoutOrderController {

    private final PaypalCheckoutOrderService paypalCheckoutOrderService;

    @PostMapping
    public ResponseAPI<PaypalCheckoutOrderResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                                              @Valid @RequestBody CreateCheckoutOrderRequest request) {
        return ResponseAPI.<PaypalCheckoutOrderResponse>builder()
                .code(200)
                .data(paypalCheckoutOrderService.create(principal.getId(), request))
                .build();
    }

    @PostMapping("/{orderId}/capture")
    public ResponseAPI<PaypalCheckoutOrderResponse> capture(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable UUID orderId) {
        return ResponseAPI.<PaypalCheckoutOrderResponse>builder()
                .code(200)
                .data(paypalCheckoutOrderService.capture(principal.getId(), orderId))
                .build();
    }

    @GetMapping("/{orderId}")
    public ResponseAPI<PaypalCheckoutOrderResponse> getById(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable UUID orderId) {
        return ResponseAPI.<PaypalCheckoutOrderResponse>builder()
                .code(200)
                .data(paypalCheckoutOrderService.getByIdForOwner(principal.getId(), orderId))
                .build();
    }
}
