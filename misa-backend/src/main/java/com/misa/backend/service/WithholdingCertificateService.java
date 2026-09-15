package com.misa.backend.service;

import com.misa.backend.dto.request.misa.CancelCertificateRequest;
import com.misa.backend.dto.request.misa.CreateWithholdingCertificateRequest;
import com.misa.backend.dto.request.misa.IncorrectRecordNotificationRequest;
import com.misa.backend.dto.request.misa.IssueCertificateRequest;
import com.misa.backend.dto.request.misa.SubmitCertificateRequest;
import com.misa.backend.dto.response.misa.CertificateCancelResponse;
import com.misa.backend.dto.response.misa.CertificateIssueResponse;
import com.misa.backend.dto.response.misa.CertificateLookupResponse;
import com.misa.backend.dto.response.misa.CertificateStatusResponse;
import com.misa.backend.dto.response.misa.CertificateSubmitResponse;
import com.misa.backend.dto.response.misa.IncorrectRecordNotificationResponse;
import com.misa.backend.dto.response.misa.WithholdingCertificateResponse;

import java.util.UUID;

public interface WithholdingCertificateService {

    WithholdingCertificateResponse create(CreateWithholdingCertificateRequest request);

    CertificateIssueResponse issue(UUID certificateId, IssueCertificateRequest request);

    CertificateSubmitResponse submit(UUID certificateId, SubmitCertificateRequest request);

    CertificateStatusResponse getStatus(UUID certificateId);

    CertificateLookupResponse lookup(String lookupCode);

    CertificateCancelResponse cancel(UUID certificateId, CancelCertificateRequest request);

    byte[] getPdf(UUID certificateId);

    String getXml(UUID certificateId);

    IncorrectRecordNotificationResponse reportIncorrectRecord(IncorrectRecordNotificationRequest request);
}
