package com.marketplace.backend.service;

import com.marketplace.backend.dto.request.job.CreateJobRequest;
import com.marketplace.backend.dto.request.job.UpdateJobRequest;
import com.marketplace.backend.dto.response.common.PageResponse;
import com.marketplace.backend.dto.response.job.JobPaymentStatusResponse;
import com.marketplace.backend.dto.response.job.JobResponse;
import com.marketplace.backend.dto.response.job.PayJobResponse;

import java.util.UUID;

public interface JobService {

    JobResponse create(UUID clientUserId, CreateJobRequest request);

    PageResponse<JobResponse> listForUser(UUID userId, int page, int size);

    JobResponse getByIdForParticipant(UUID userId, UUID jobId);

    JobResponse update(UUID clientUserId, UUID jobId, UpdateJobRequest request);

    PayJobResponse pay(UUID clientUserId, UUID jobId);

    JobResponse confirmPayment(UUID clientUserId, UUID jobId);

    JobResponse approve(UUID clientUserId, UUID jobId);

    JobResponse cancel(UUID clientUserId, UUID jobId);

    JobPaymentStatusResponse getPaymentStatus(UUID userId, UUID jobId);
}
