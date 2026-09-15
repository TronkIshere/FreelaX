package com.paypal.backend.dto.request.paypal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatePaypalPayeeRequest {

    @NotBlank(message = "Họ tên không được để trống")
    String fullName;

    @NotBlank(message = "Email PayPal không được để trống")
    @Email(message = "Email PayPal không hợp lệ")
    String paypalEmail;

    String phone;

    String address;

    String nationality;
}
