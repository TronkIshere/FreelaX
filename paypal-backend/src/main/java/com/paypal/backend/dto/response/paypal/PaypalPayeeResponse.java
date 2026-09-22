package com.paypal.backend.dto.response.paypal;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaypalPayeeResponse {
    UUID id;
    String fullName;
    String paypalEmail;
    String phone;
    String address;
    String nationality;
    boolean active;
}
