package com.paypal.backend.dto.request.paypal;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReleasePayoutRequest {

    @NotNull(message = "checkoutOrderId không được để trống")
    UUID checkoutOrderId;
}
