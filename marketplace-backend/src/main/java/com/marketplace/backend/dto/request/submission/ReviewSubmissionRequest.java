package com.marketplace.backend.dto.request.submission;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ReviewSubmissionRequest {
    public enum Decision { APPROVE, REQUEST_REVISION, OPEN_DISPUTE }

    @NotNull
    private Decision decision;
    @Size(max = 10000)
    private String note;
    @Size(max = 10000)
    private String feedback;
    @Size(max = 20)
    private List<UUID> criterionIds = List.of();
    @Size(max = 10)
    private List<UUID> deliverableIds = List.of();

    @Size(max = 60)
    private String reasonCode;

    @Size(max = 2000)
    private String description;
}
