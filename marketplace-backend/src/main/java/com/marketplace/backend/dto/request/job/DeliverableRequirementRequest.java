package com.marketplace.backend.dto.request.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeliverableRequirementRequest {
    @NotBlank(message = "Tên sản phẩm bàn giao không được để trống")
    @Size(max = 200, message = "Tên sản phẩm bàn giao không được vượt quá 200 ký tự")
    private String title;
    @NotBlank(message = "Mô tả sản phẩm bàn giao không được để trống")
    @Size(max = 2000, message = "Mô tả sản phẩm bàn giao không được vượt quá 2000 ký tự")
    private String description;
    private boolean required = true;
}
