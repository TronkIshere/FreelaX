package com.marketplace.backend.dto.response.job;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class RequirementResponse {
    UUID id;
    String title;
    String description;
    boolean required;
    int order;
}
