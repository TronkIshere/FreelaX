package com.misa.backend.dto.response.misa;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateStatusResponse {
    UUID certificateId;
    String status;
    String submissionId;
    String taxAuthorityReference;
    LocalDateTime updatedAt;
}
