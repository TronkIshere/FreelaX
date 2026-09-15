package com.misa.backend.dto.response.misa;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TaxpayerResponse {
    UUID id;
    String fullName;
    String address;
    String phone;
    String taxCode;
    String identityNumber;
    String nationality;
    boolean active;
}
