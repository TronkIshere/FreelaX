package com.marketplace.backend.dto.request.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateContractSubmissionRequest {
    @NotBlank
    @Size(max = 10000)
    private String summary;

    @Valid
    @Size(max = 10)
    private List<DeliverableInput> deliverables = List.of();

    @Valid
    @Size(max = 20)
    private List<CriterionInput> acceptanceEvidence = List.of();

    @Getter
    @Setter
    public static class DeliverableInput {
        @NotNull
        private UUID requirementId;
        @NotBlank
        @Size(max = 2048)
        private String url;
        @Size(max = 2000)
        private String description;
    }

    @Getter
    @Setter
    public static class CriterionInput {
        @NotNull
        private UUID criterionId;
        @Size(max = 2048)
        private String url;
        @Size(max = 2000)
        private String note;
    }
}
