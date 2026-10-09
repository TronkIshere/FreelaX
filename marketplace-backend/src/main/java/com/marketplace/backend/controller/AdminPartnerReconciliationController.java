package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.service.PartnerReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/partner-reconciliation")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminPartnerReconciliationController {
    private final PartnerReconciliationService service;

    @GetMapping
    public ResponseAPI<PartnerReconciliationService.ReconciliationView> get() {
        return ResponseAPI.<PartnerReconciliationService.ReconciliationView>builder()
                .code(200).data(service.snapshot()).build();
    }
}
