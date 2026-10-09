package com.marketplace.backend.service;

import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.tax.TaxCertificateFile;
import com.marketplace.backend.dto.response.tax.TaxCertificateResponse;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.Job;

import java.util.List;
import java.util.UUID;

public interface TaxCertificateService {

    /** Unified rail: issue the withholding certificate once the VND payout statement is confirmed. */
    void exportForUnifiedPayout(UUID paymentFlowId);

    void exportForPayout(Job job, FreelancerPayoutRecord payoutRecord, String transactionReference);

    PageResponse<TaxCertificateResponse> listForUser(UUID userId, int page, int size);

    TaxCertificateResponse getForParticipant(UUID userId, UUID taxRecordId);

    TaxCertificateResponse getByJobForParticipant(UUID userId, UUID jobId);

    TaxCertificateResponse syncForParticipant(UUID userId, UUID taxRecordId);

    TaxCertificateResponse retryExportForParticipant(UUID userId, UUID taxRecordId);

    TaxCertificateFile downloadPdfForParticipant(UUID userId, UUID taxRecordId);

    TaxCertificateFile downloadXmlForParticipant(UUID userId, UUID taxRecordId);

    void syncById(UUID taxRecordId);

    List<UUID> findRecordIdsToSync();
}