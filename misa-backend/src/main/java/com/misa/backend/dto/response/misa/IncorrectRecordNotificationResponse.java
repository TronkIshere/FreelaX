package com.misa.backend.dto.response.misa;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IncorrectRecordNotificationResponse {
    UUID notificationId;
    UUID certificateId;
    String status;
    String nextAction;
}
