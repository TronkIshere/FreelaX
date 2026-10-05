package com.payment.backend.controller;
import com.payment.backend.dto.request.bofa.CreateCheckoutRefundRequest;
import com.payment.backend.dto.response.bofa.BofaCheckoutRefundResponse;
import com.payment.backend.dto.response.common.ResponseAPI;
import com.payment.backend.service.BofaCheckoutRefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/internal/BofA/refunds")
public class BofaCheckoutRefundController {
    private final BofaCheckoutRefundService refunds;
    @PostMapping public ResponseAPI<BofaCheckoutRefundResponse> refund(@Valid @RequestBody CreateCheckoutRefundRequest request) {
        return ResponseAPI.<BofaCheckoutRefundResponse>builder().code(200).data(refunds.refund(request)).build();
    }
    @GetMapping("/by-key") public ResponseAPI<BofaCheckoutRefundResponse> get(@RequestParam String refundKey) {
        return ResponseAPI.<BofaCheckoutRefundResponse>builder().code(200).data(refunds.getByRefundKey(refundKey)).build();
    }
}
