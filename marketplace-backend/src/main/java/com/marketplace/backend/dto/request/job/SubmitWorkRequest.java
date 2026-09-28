package com.marketplace.backend.dto.request.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitWorkRequest {
    @NotBlank(message = "summary không được để trống")
    @Size(max = 10000, message = "summary không được vượt quá 10000 ký tự")
    String summary;

    @Size(max = 2048, message = "deliverableUrl không được vượt quá 2048 ký tự")
    String deliverableUrl;
}
