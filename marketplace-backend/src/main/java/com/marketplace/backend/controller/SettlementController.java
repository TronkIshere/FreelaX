package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.settlement.SettlementResponse;
import com.marketplace.backend.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contracts/{contractId}/settlement")
public class SettlementController {
    private final SettlementService settlements;

    @GetMapping
    public ResponseAPI<SettlementResponse> get(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable UUID contractId) {
        return ResponseAPI.<SettlementResponse>builder().code(200)
                .data(settlements.get(principal.getId(), contractId)).build();
    }
}
