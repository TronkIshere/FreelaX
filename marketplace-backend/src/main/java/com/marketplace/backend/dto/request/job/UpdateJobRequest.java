package com.marketplace.backend.dto.request.job;

import jakarta.validation.constraints.DecimalMin;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateJobRequest {

    String title;

    String description;

    String category;

    @Size(max = 10, message = "Tối đa 10 kỹ năng công việc")
    List<String> skills;

    @DecimalMin(value = "0.01", message = "budgetUsd phải lớn hơn 0")
    BigDecimal budgetUsd;
}
