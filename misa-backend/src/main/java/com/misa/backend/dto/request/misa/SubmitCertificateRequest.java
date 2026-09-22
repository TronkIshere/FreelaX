package com.misa.backend.dto.request.misa;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubmitCertificateRequest {

    String submissionMode = "ELECTRONIC";

    @NotBlank(message = "Idempotency key không được để trống")
    String idempotencyKey;
}
