package com.misa.backend.dto.request.misa;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CancelCertificateRequest {

    @NotBlank(message = "Lý do hủy không được để trống")
    String reason;
}
