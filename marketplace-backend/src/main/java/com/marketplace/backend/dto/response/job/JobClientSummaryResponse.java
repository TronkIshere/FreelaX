package com.marketplace.backend.dto.response.job;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class JobClientSummaryResponse {
    UUID id;
    String displayName;
}
