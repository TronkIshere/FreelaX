package com.misa.backend.dto.request.misa;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateTaxpayerRequest {

    @NotBlank(message = "Họ tên không được để trống")
    String fullName;

    String address;

    String phone;

    String taxCode;

    @NotBlank(message = "Số định danh cá nhân không được để trống")
    String identityNumber;

    @NotBlank(message = "Quốc tịch không được để trống")
    String nationality;
}
