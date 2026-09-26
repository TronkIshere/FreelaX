package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.notification.NotificationResponse;
import com.marketplace.backend.entity.NotificationType;

import java.util.UUID;

public interface NotificationService {

    void notify(UUID recipientUserId, NotificationType type, String title, String message, UUID jobId);

    PageResponse<NotificationResponse> listForUser(UUID userId, int page, int size);

    NotificationResponse markAsRead(UUID userId, UUID notificationId);
}