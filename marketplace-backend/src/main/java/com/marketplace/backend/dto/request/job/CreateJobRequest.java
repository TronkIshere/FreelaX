package com.marketplace.backend.dto.request.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateJobRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    String title;

    String description;

    @NotNull(message = "Ngân sách không được để trống")
    @Positive(message = "Ngân sách phải lớn hơn 0")
    BigDecimal budgetUsd;

    @NotNull(message = "Hạn bàn giao không được để trống")
    @Future(message = "Hạn bàn giao phải ở tương lai")
    Instant deliveryDueAt;

    @Min(value = 24, message = "Thời gian review tối thiểu là 24 giờ")
    @Max(value = 168, message = "Thời gian review tối đa là 168 giờ")
    int reviewWindowHours = 72;

    @Min(value = 0, message = "Số lần chỉnh sửa không được âm")
    @Max(value = 2, message = "MVP hỗ trợ tối đa 2 lần chỉnh sửa")
    int maxRevisions = 2;

    @Valid
    @NotEmpty(message = "Phải có ít nhất một sản phẩm bàn giao")
    @Size(max = 10, message = "Tối đa 10 sản phẩm bàn giao")
    List<DeliverableRequirementRequest> deliverables;

    @Valid
    @NotEmpty(message = "Phải có ít nhất một điều kiện nghiệm thu")
    @Size(max = 20, message = "Tối đa 20 điều kiện nghiệm thu")
    List<AcceptanceCriterionRequest> acceptanceCriteria;
}
