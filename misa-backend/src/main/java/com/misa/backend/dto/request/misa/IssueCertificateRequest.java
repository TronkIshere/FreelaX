package com.misa.backend.dto.request.misa;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IssueCertificateRequest {

    String digitalCertificateSerial;

    String signatureMode = "DIGITAL_SIGNATURE";
}
