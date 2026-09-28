package com.marketplace.backend.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "misa-backend.certificate")
public class MisaCertificateProperties {
    private boolean autoIssue = false;
    private boolean autoSubmit = false;
    private String digitalCertificateSerial;
    private String signatureMode = "DIGITAL_SIGNATURE";
    private String submissionMode = "ELECTRONIC";
}