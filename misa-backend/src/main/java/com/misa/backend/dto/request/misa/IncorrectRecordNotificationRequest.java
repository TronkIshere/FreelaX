package com.misa.backend.dto.request.misa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IncorrectRecordNotificationRequest {

    @NotNull(message = "Mã chứng từ không được để trống")
    UUID certificateId;

    @NotBlank(message = "Loại lỗi không được để trống")
    String errorType;

    String description;

    @NotBlank(message = "Hành động yêu cầu không được để trống")
    String requestedAction;
}
