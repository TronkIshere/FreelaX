package com.paypal.backend.controller;

import com.paypal.backend.dto.response.common.ResponseAPI;
import com.paypal.backend.dto.response.paypal.PaypalPayeeStatusResponse;
import com.paypal.backend.service.PaypalPayeeStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/paypal/users")
@RequiredArgsConstructor
public class PaypalPayeeStatusController {

    private final PaypalPayeeStatusService paypalPayeeStatusService;

    @GetMapping("/{userId}/payee-status")
    public ResponseAPI<PaypalPayeeStatusResponse> getStatus(@PathVariable UUID userId) {
        return ResponseAPI.<PaypalPayeeStatusResponse>builder()
                .code(200)
                .data(paypalPayeeStatusService.getStatus(userId))
                .build();
    }
}
