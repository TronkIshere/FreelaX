package com.misa.backend.dto.request.misa;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateWithholdingCertificateRequest {

    @NotNull(message = "Mã giao dịch payout không được để trống")
    UUID payoutTransactionId;

    @Size(max = 100)
    String idempotencyKey;
}
