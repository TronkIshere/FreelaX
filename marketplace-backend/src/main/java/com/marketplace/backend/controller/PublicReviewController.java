package com.marketplace.backend.controller;

import com.marketplace.backend.dto.response.common.ResponseAPI;
import com.marketplace.backend.dto.response.review.ReviewResponse;
import com.marketplace.backend.service.ContractReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles/{userId}/reviews")
@RequiredArgsConstructor
public class PublicReviewController {
    private final ContractReviewService service;

    @GetMapping
    public ResponseAPI<List<ReviewResponse>> list(@PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseAPI.<List<ReviewResponse>>builder().code(200)
                .data(service.publicForUser(userId, page, size)).build();
    }
}
