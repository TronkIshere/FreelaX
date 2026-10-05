package com.marketplace.backend.dto.request.review;

public record ModerateReviewRequest(Action action, String reason) {
    public enum Action { HIDE_CONTENT, INVALIDATE }
}
