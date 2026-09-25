package com.marketplace.backend.dto.response.misa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import java.util.UUID;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MisaCertificateResult {
    UUID id;
    String status;
}