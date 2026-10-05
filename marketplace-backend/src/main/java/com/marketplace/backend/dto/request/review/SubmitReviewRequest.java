package com.marketplace.backend.dto.request.review;

public record SubmitReviewRequest(Integer overall, Dimensions dimensions, String comment) {
    public record Dimensions(Integer communication, Integer requirementsOrQuality, Integer timeliness) {}
}
