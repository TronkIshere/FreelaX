package com.marketplace.backend.dto.request.dispute;

import java.util.List;

public record OpenDisputeRequest(String reasonCode, String description, List<DisputeEvidenceInput> evidence) {}
