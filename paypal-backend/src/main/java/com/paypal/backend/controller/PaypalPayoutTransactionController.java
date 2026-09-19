package com.paypal.backend.controller;

import com.paypal.backend.configuration.UserPrincipal;
import com.paypal.backend.dto.request.paypal.RecordPaypalPayoutRequest;
import com.paypal.backend.dto.request.paypal.WithdrawPaypalPayoutRequest;
import com.paypal.backend.dto.response.common.ResponseAPI;
import com.paypal.backend.dto.response.paypal.PaypalPayoutTransactionResponse;
import com.paypal.backend.service.PaypalPayoutTransactionService;
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
@RequestMapping("/api/v1/paypal/payees/{payeeId}/transactions")
@RequiredArgsConstructor
public class PaypalPayoutTransactionController {

    private final PaypalPayoutTransactionService paypalPayoutTransactionService;

    @PostMapping
    public ResponseAPI<PaypalPayoutTransactionResponse> record(@AuthenticationPrincipal UserPrincipal principal,
                                                               @PathVariable UUID payeeId,
                                                               @Valid @RequestBody RecordPaypalPayoutRequest request) {
        return ResponseAPI.<PaypalPayoutTransactionResponse>builder()
                .code(200)
                .message("Ghi nhận giao dịch nhận tiền qua PayPal thành công")
                .data(paypalPayoutTransactionService.record(principal.getId(), payeeId, request))
                .build();
    }

    @GetMapping("/{transactionId}")
    public ResponseAPI<PaypalPayoutTransactionResponse> getById(@AuthenticationPrincipal UserPrincipal principal,
                                                                @PathVariable UUID payeeId,
                                                                @PathVariable UUID transactionId) {
        return ResponseAPI.<PaypalPayoutTransactionResponse>builder()
                .code(200)
                .data(paypalPayoutTransactionService.getById(principal.getId(), payeeId, transactionId))
                .build();
    }

    @PostMapping("/{transactionId}/withdraw")
    public ResponseAPI<PaypalPayoutTransactionResponse> withdraw(@AuthenticationPrincipal UserPrincipal principal,
                                                                 @PathVariable UUID payeeId,
                                                                 @PathVariable UUID transactionId,
                                                                 @Valid @RequestBody WithdrawPaypalPayoutRequest request) {
        return ResponseAPI.<PaypalPayoutTransactionResponse>builder()
                .code(200)
                .message("Đã ghi nhận rút tiền về ngân hàng")
                .data(paypalPayoutTransactionService.withdraw(principal.getId(), payeeId, transactionId, request))
                .build();
    }
}