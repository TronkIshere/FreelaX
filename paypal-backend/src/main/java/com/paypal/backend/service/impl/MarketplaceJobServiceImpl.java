package com.paypal.backend.service.impl;

import com.paypal.backend.entity.JobStatus;
import com.paypal.backend.entity.MarketplaceJob;
import com.paypal.backend.exception.ApplicationException;
import com.paypal.backend.exception.ErrorCode;
import com.paypal.backend.marketplace.dto.request.CreateJobRequest;
import com.paypal.backend.marketplace.dto.request.LinkCheckoutOrderRequest;
import com.paypal.backend.marketplace.dto.response.JobResponse;
import com.paypal.backend.marketplace.service.MarketplaceJobService;
import com.paypal.backend.repository.MarketplaceJobRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceJobServiceImpl implements MarketplaceJobService {

    MarketplaceJobRepository marketplaceJobRepository;

    @Override
    @Transactional
    public JobResponse create(UUID clientUserId, CreateJobRequest request) {
        MarketplaceJob job = new MarketplaceJob();
        job.setClientUserId(clientUserId);
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setBudgetUsd(request.getBudgetUsd());
        job.setStatus(JobStatus.OPEN);
        job.setCreatedAt(LocalDateTime.now());

        marketplaceJobRepository.save(job);

        return toResponse(job);
    }

    @Override
    public JobResponse getById(UUID jobId) {
        MarketplaceJob job = marketplaceJobRepository.findById(jobId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.JOB_NOT_FOUND, jobId));
        return toResponse(job);
    }

    @Override
    public List<JobResponse> listOpenJobs() {
        return marketplaceJobRepository.findByStatus(JobStatus.OPEN)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public JobResponse linkCheckoutOrder(UUID clientUserId, UUID jobId, LinkCheckoutOrderRequest request) {
        MarketplaceJob job = marketplaceJobRepository.findById(jobId)
                .filter(j -> j.getClientUserId().equals(clientUserId))
                .orElseThrow(() -> new ApplicationException(ErrorCode.JOB_NOT_FOUND, jobId));

        job.setCheckoutOrderId(request.getCheckoutOrderId());
        job.setStatus(JobStatus.IN_PROGRESS);
        marketplaceJobRepository.save(job);

        return toResponse(job);
    }

    private JobResponse toResponse(MarketplaceJob job) {
        return JobResponse.builder()
                .id(job.getId())
                .clientUserId(job.getClientUserId())
                .title(job.getTitle())
                .description(job.getDescription())
                .budgetUsd(job.getBudgetUsd())
                .checkoutOrderId(job.getCheckoutOrderId())
                .status(job.getStatus().name())
                .createdAt(job.getCreatedAt())
                .build();
    }
}
