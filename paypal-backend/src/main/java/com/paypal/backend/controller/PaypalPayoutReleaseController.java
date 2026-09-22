package com.paypal.backend.controller;

import com.paypal.backend.dto.request.paypal.ReleasePayoutRequest;
import com.paypal.backend.dto.response.common.ResponseAPI;
import com.paypal.backend.dto.response.paypal.PaypalPayoutReleaseResponse;
import com.paypal.backend.service.PaypalPayoutReleaseService;
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
@RequestMapping("/internal/paypal/payouts")
@RequiredArgsConstructor
public class PaypalPayoutReleaseController {

    private final PaypalPayoutReleaseService paypalPayoutReleaseService;

    @PostMapping
    public ResponseAPI<PaypalPayoutReleaseResponse> release(@Valid @RequestBody ReleasePayoutRequest request) {
        return ResponseAPI.<PaypalPayoutReleaseResponse>builder()
                .code(200)
                .data(paypalPayoutReleaseService.release(request))
                .build();
    }

    @GetMapping("/{id}")
    public ResponseAPI<PaypalPayoutReleaseResponse> getById(@PathVariable UUID id) {
        return ResponseAPI.<PaypalPayoutReleaseResponse>builder()
                .code(200)
                .data(paypalPayoutReleaseService.getById(id))
                .build();
    }
}
