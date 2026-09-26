package com.marketplace.backend.dto.request.job;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssignFreelancerRequest {
    @NotNull(message = "freelancerId không được để trống")
    UUID freelancerId;
}