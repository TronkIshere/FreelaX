package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.job.AssignFreelancerRequest;
import com.marketplace.backend.dto.request.job.CreateJobRequest;
import com.marketplace.backend.dto.request.job.RequestRevisionRequest;
import com.marketplace.backend.dto.request.job.SubmitWorkRequest;
import com.marketplace.backend.dto.request.job.UpdateJobRequest;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.job.CertificateSummaryResponse;
import com.marketplace.backend.dto.response.job.DiscoverJobResponse;
import com.marketplace.backend.dto.response.job.JobApplicationResponse;
import com.marketplace.backend.dto.response.job.JobPaymentStatusResponse;
import com.marketplace.backend.dto.response.job.JobResponse;
import com.marketplace.backend.dto.response.job.JobSubmissionResponse;
import com.marketplace.backend.dto.response.job.MyApplicationResponse;
import com.marketplace.backend.entity.JobApplicationStatus;

import java.math.BigDecimal;

import java.util.List;
import java.util.UUID;

public interface JobService {

    JobResponse create(UUID clientUserId, CreateJobRequest request);

    PageResponse<JobResponse> listForUser(UUID userId, int page, int size);

    PageResponse<DiscoverJobResponse> discover(UUID freelancerId, int page, int size, String keyword,
                                                BigDecimal minBudgetUsd, BigDecimal maxBudgetUsd,
                                                String sort, String application);

    PageResponse<MyApplicationResponse> listMyApplications(UUID freelancerId, int page, int size,
                                                            JobApplicationStatus status);

    JobResponse getByIdForParticipant(UUID userId, UUID jobId);

    JobResponse update(UUID clientUserId, UUID jobId, UpdateJobRequest request);

    JobResponse approve(UUID clientUserId, UUID jobId);

    JobSubmissionResponse submitWork(UUID freelancerId, UUID jobId, SubmitWorkRequest request);

    JobSubmissionResponse requestRevision(UUID clientUserId, UUID jobId, RequestRevisionRequest request);

    List<JobSubmissionResponse> listSubmissions(UUID userId, UUID jobId);

    JobResponse cancel(UUID clientUserId, UUID jobId);

    JobPaymentStatusResponse getPaymentStatus(UUID userId, UUID jobId);

    JobResponse assignFreelancer(UUID clientUserId, UUID jobId, AssignFreelancerRequest request);

    JobApplicationResponse apply(UUID freelancerId, UUID jobId);

    List<JobApplicationResponse> listApplications(UUID clientUserId, UUID jobId);

    List<CertificateSummaryResponse> listCertificatesForFreelancer(UUID freelancerId);

    byte[] downloadCertificatePdf(UUID freelancerId, UUID certificateId);
}
