package com.paypal.backend.marketplace.service;

import com.paypal.backend.marketplace.dto.request.CreateJobRequest;
import com.paypal.backend.marketplace.dto.request.LinkCheckoutOrderRequest;
import com.paypal.backend.marketplace.dto.response.JobResponse;

import java.util.List;
import java.util.UUID;

public interface MarketplaceJobService {

    JobResponse create(UUID clientUserId, CreateJobRequest request);

    JobResponse getById(UUID jobId);

    List<JobResponse> listOpenJobs();

    JobResponse linkCheckoutOrder(UUID clientUserId, UUID jobId, LinkCheckoutOrderRequest request);
}
