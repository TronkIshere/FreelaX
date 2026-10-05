package com.marketplace.backend.dto.request.dispute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OpenDisputeRequest(String reasonCode,
                                 @NotBlank @Size(max = 2000) String description,
                                 List<DisputeEvidenceInput> evidence) {}
