package com.misa.backend.dto.response.misa;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateLookupResponse {
    UUID certificateId;
    String lookupCode;
    String status;
    String formNumber;
    String symbol;
    String number;
    TaxpayerSummary taxpayer;
    CertificateDocumentLinks documents;
}
