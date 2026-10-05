package com.marketplace.backend.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.profile.PortfolioResponse;
import com.marketplace.backend.dto.response.profile.ProfileResponse;
import com.marketplace.backend.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService service;

    @GetMapping("/me")
    public ResponseAPI<ProfileResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ok(service.me(principal.getId()));
    }

    @PatchMapping("/me")
    public ResponseAPI<ProfileResponse> patch(@AuthenticationPrincipal UserPrincipal principal,
                                               @RequestBody ObjectNode request) {
        return ok(service.patch(principal.getId(), request));
    }

    @PutMapping("/me/skills")
    public ResponseAPI<ProfileResponse> skills(@AuthenticationPrincipal UserPrincipal principal,
                                                @RequestBody ObjectNode request) {
        return ok(service.replaceSkills(principal.getId(), request));
    }

    @GetMapping("/{userId}")
    public ResponseAPI<ProfileResponse> publicProfile(@PathVariable UUID userId) {
        return ok(service.publicProfile(userId));
    }

    @GetMapping("/{userId}/portfolio")
    public ResponseAPI<List<PortfolioResponse>> portfolio(@PathVariable UUID userId) {
        return ok(service.portfolio(userId));
    }

    @PostMapping("/me/portfolio")
    public ResponseAPI<PortfolioResponse> addPortfolio(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestBody ObjectNode request) {
        return ok(service.addPortfolio(principal.getId(), request));
    }

    @PatchMapping("/me/portfolio/{itemId}")
    public ResponseAPI<PortfolioResponse> patchPortfolio(@AuthenticationPrincipal UserPrincipal principal,
                                                          @PathVariable UUID itemId,
                                                          @RequestBody ObjectNode request) {
        return ok(service.patchPortfolio(principal.getId(), itemId, request));
    }

    @DeleteMapping("/me/portfolio/{itemId}")
    public ResponseAPI<Void> deletePortfolio(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID itemId) {
        service.deletePortfolio(principal.getId(), itemId);
        return ok(null);
    }

    private <T> ResponseAPI<T> ok(T value) {
        return ResponseAPI.<T>builder().code(200).data(value).build();
    }
}
