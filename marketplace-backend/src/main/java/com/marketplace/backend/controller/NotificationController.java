package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.UserPrincipal;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.notification.NotificationResponse;
import com.marketplace.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseAPI<PageResponse<NotificationResponse>> list(@AuthenticationPrincipal UserPrincipal principal,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "10") int size) {
        return ResponseAPI.<PageResponse<NotificationResponse>>builder()
                .code(200)
                .data(notificationService.listForUser(principal.getId(), page, size))
                .build();
    }

    @PatchMapping("/{id}/read")
    public ResponseAPI<NotificationResponse> markAsRead(@AuthenticationPrincipal UserPrincipal principal,
                                                        @PathVariable UUID id) {
        return ResponseAPI.<NotificationResponse>builder()
                .code(200)
                .message("Đã đánh dấu đã đọc")
                .data(notificationService.markAsRead(principal.getId(), id))
                .build();
    }
}