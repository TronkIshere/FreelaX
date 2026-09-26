package com.marketplace.backend.dto.response.notification;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationResponse {
    UUID id;
    String title;
    String message;
    String type;
    UUID jobId;
    boolean read;
    LocalDateTime createdAt;
}