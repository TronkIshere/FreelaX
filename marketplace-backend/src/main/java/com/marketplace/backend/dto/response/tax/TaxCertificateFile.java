package com.marketplace.backend.dto.response.tax;

public record TaxCertificateFile(
        String fileName,
        byte[] content
) {}