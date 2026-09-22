package com.misa.backend.controller;

import com.misa.backend.configuration.UserPrincipal;
import com.misa.backend.dto.request.misa.CreateTaxpayerRequest;
import com.misa.backend.dto.response.common.ResponseAPI;
import com.misa.backend.dto.response.misa.TaxpayerResponse;
import com.misa.backend.service.TaxpayerService;
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
@RequestMapping("/api/v1/taxpayers")
@RequiredArgsConstructor
public class TaxpayerController {

    private final TaxpayerService taxpayerService;

    @PostMapping
    public ResponseAPI<TaxpayerResponse> register(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody CreateTaxpayerRequest request) {
        return ResponseAPI.<TaxpayerResponse>builder()
                .code(200)
                .message("Đăng ký hồ sơ người nộp thuế thành công")
                .data(taxpayerService.register(principal.getId(), request))
                .build();
    }

    @GetMapping("/me")
    public ResponseAPI<TaxpayerResponse> getMine(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseAPI.<TaxpayerResponse>builder()
                .code(200)
                .data(taxpayerService.getByUserId(principal.getId()))
                .build();
    }

    @GetMapping("/{id}")
    public ResponseAPI<TaxpayerResponse> getById(@PathVariable UUID id) {
        return ResponseAPI.<TaxpayerResponse>builder()
                .code(200)
                .data(taxpayerService.getById(id))
                .build();
    }
}
