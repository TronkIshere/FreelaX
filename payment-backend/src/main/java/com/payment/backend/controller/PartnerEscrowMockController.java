package com.payment.backend.controller;

import com.payment.backend.dto.response.common.ResponseAPI;
import com.payment.backend.service.PartnerEscrowMockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/partner-mock/escrows")
@RequiredArgsConstructor
public class PartnerEscrowMockController {
    private final PartnerEscrowMockService service;

    @PostMapping
    public ResponseAPI<PartnerEscrowMockService.EscrowView> open(
            @RequestBody PartnerEscrowMockService.OpenRequest request) {
        return ok(service.open(request));
    }
    @GetMapping("/{milestoneId}")
    public ResponseAPI<PartnerEscrowMockService.EscrowView> get(@PathVariable UUID milestoneId) {
        return ok(service.get(milestoneId));
    }
    @PostMapping("/{milestoneId}/freeze")
    public ResponseAPI<PartnerEscrowMockService.EscrowView> freeze(@PathVariable UUID milestoneId) {
        return ok(service.freeze(milestoneId));
    }
    @PostMapping("/{milestoneId}/release")
    public ResponseAPI<PartnerEscrowMockService.EscrowView> release(@PathVariable UUID milestoneId,
            @RequestBody PartnerEscrowMockService.ReleaseRequest request,
            @RequestParam(defaultValue = "false") boolean adminResolution) {
        return ok(service.release(milestoneId, request, adminResolution));
    }
    @PostMapping("/{milestoneId}/refund")
    public ResponseAPI<PartnerEscrowMockService.EscrowView> refund(@PathVariable UUID milestoneId,
            @RequestBody PartnerEscrowMockService.RefundRequest request,
            @RequestParam(defaultValue = "false") boolean adminResolution) {
        return ok(service.refund(milestoneId, request, adminResolution));
    }
    @GetMapping("/statement")
    public ResponseAPI<PartnerEscrowMockService.StatementView> statement() {
        return ok(service.statement());
    }
    private <T> ResponseAPI<T> ok(T data) {
        return ResponseAPI.<T>builder().code(200).data(data).build();
    }
}
