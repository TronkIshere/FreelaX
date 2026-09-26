package com.marketplace.backend.service.impl;

import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.notification.NotificationResponse;
import com.marketplace.backend.entity.Notification;
import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.NotificationRepository;
import com.marketplace.backend.service.NotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationServiceImpl implements NotificationService {

    NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void notify(UUID recipientUserId, NotificationType type, String title, String message, UUID jobId) {
        if (recipientUserId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setJobId(jobId);
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    @Override
    public PageResponse<NotificationResponse> listForUser(UUID userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending());
        Page<Notification> notificationPage = notificationRepository.findByRecipientUserId(userId, pageable);

        return PageResponse.<NotificationResponse>builder()
                .currentPage(notificationPage.getNumber())
                .pageSize(notificationPage.getSize())
                .totalPages(notificationPage.getTotalPages())
                .totalElements(notificationPage.getTotalElements())
                .data(notificationPage.getContent().stream().map(this::toResponse).collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getRecipientUserId().equals(userId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOTIFICATION_NOT_FOUND, notificationId));

        notification.setRead(true);
        notificationRepository.save(notification);

        return toResponse(notification);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType().name())
                .jobId(n.getJobId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}