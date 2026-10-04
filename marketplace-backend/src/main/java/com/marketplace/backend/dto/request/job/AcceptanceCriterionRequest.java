package com.marketplace.backend.dto.request.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AcceptanceCriterionRequest {
    @NotBlank(message = "Mô tả điều kiện nghiệm thu không được để trống")
    @Size(max = 1000, message = "Mô tả điều kiện nghiệm thu không được vượt quá 1000 ký tự")
    private String description;
    private boolean required = true;
}
