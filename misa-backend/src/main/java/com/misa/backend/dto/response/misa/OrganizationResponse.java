package com.misa.backend.dto.response.misa;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrganizationResponse {
    String id;
    String name;
    String taxCode;
    String address;
    String phone;
    String taxAuthority;
    String status;
}
