package com.marketplace.backend.dto.request.auth;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LinkMisaTaxpayerRequest {

    @NotNull(message = "Mã người nộp thuế bên misa-backend không được để trống")
    UUID misaTaxpayerId;
}