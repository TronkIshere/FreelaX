package com.marketplace.backend.dto.request.dispute;

import com.marketplace.backend.entity.DisputeEvidence;

public record DisputeEvidenceInput(DisputeEvidence.Kind kind, String text, String url, String sha256) {}
