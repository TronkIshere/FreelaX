package com.marketplace.backend.dto.request.dispute;

public record NegotiateDisputeRequest(ResolveDisputeRequest.Outcome outcome, String reason) {}
