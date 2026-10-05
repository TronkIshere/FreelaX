package com.marketplace.backend.dto.response.profile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PortfolioResponse(UUID id, UUID userId, long version, String title,
                                String description, String projectUrl, String thumbnailUrl,
                                List<String> skills, LocalDate completedAt, int sortOrder) {}
