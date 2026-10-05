package com.marketplace.backend.dto.request.cancellation;
import jakarta.validation.constraints.*;
public record CreateCancellationRequest(@NotBlank @Size(max = 60) String reasonCode,
                                        @NotBlank @Size(max = 2000) String description) { }
