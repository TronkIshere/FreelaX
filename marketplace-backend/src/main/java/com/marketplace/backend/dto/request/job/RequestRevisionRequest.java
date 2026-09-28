package com.marketplace.backend.dto.request.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestRevisionRequest {
    @NotBlank(message = "feedback không được để trống")
    @Size(max = 10000, message = "feedback không được vượt quá 10000 ký tự")
    String feedback;
}
