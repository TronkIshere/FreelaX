package com.payment.backend.controller;

import com.payment.backend.dto.request.bofa.CreatePayoutReleaseRequest;
import com.payment.backend.dto.response.bofa.BofaPayoutReleaseResponse;
import com.payment.backend.dto.response.common.ResponseAPI;
import com.payment.backend.service.BofaPayoutReleaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/BofA/payout/releases")
@RequiredArgsConstructor
public class BofaPayoutReleaseController {
    private final BofaPayoutReleaseService releases;

    @PostMapping
    public ResponseAPI<BofaPayoutReleaseResponse> release(@Valid @RequestBody CreatePayoutReleaseRequest request) {
        return envelope(releases.release(request));
    }

    @GetMapping("/by-key")
    public ResponseAPI<BofaPayoutReleaseResponse> byKey(@RequestParam String releaseKey) {
        return envelope(releases.getByReleaseKey(releaseKey));
    }

    @GetMapping("/by-checkout/{checkoutOrderId}")
    public ResponseAPI<BofaPayoutReleaseResponse> byCheckout(@PathVariable UUID checkoutOrderId) {
        return envelope(releases.getByCheckoutOrderId(checkoutOrderId));
    }

    private ResponseAPI<BofaPayoutReleaseResponse> envelope(BofaPayoutReleaseResponse result) {
        return ResponseAPI.<BofaPayoutReleaseResponse>builder().code(200).data(result).build();
    }
}
