package com.paypal.backend.controller;

import com.paypal.backend.configuration.UserPrincipal;
import com.paypal.backend.dto.request.paypal.CreatePaypalPayeeRequest;
import com.paypal.backend.dto.response.common.ResponseAPI;
import com.paypal.backend.dto.response.paypal.PaypalPayeeResponse;
import com.paypal.backend.service.PaypalPayeeService;
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
@RequestMapping("/api/v1/paypal/payees")
@RequiredArgsConstructor
public class PaypalPayeeController {

    private final PaypalPayeeService paypalPayeeService;

    @PostMapping
    public ResponseAPI<PaypalPayeeResponse> register(@AuthenticationPrincipal UserPrincipal principal,
                                                     @Valid @RequestBody CreatePaypalPayeeRequest request) {
        return ResponseAPI.<PaypalPayeeResponse>builder()
                .code(200)
                .message("Đăng ký hồ sơ nhận tiền PayPal thành công")
                .data(paypalPayeeService.register(principal.getId(), request))
                .build();
    }

    @GetMapping("/me")
    public ResponseAPI<PaypalPayeeResponse> getMine(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseAPI.<PaypalPayeeResponse>builder()
                .code(200)
                .data(paypalPayeeService.getByUserId(principal.getId()))
                .build();
    }

    @GetMapping("/{id}")
    public ResponseAPI<PaypalPayeeResponse> getById(@AuthenticationPrincipal UserPrincipal principal,
                                                    @PathVariable UUID id) {
        return ResponseAPI.<PaypalPayeeResponse>builder()
                .code(200)
                .data(paypalPayeeService.getByIdForOwner(principal.getId(), id))
                .build();
    }
}