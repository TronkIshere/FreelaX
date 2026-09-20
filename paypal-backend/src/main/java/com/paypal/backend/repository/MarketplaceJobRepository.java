package com.paypal.backend.repository;

import com.paypal.backend.entity.JobStatus;
import com.paypal.backend.entity.MarketplaceJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MarketplaceJobRepository extends JpaRepository<MarketplaceJob, UUID> {

    List<MarketplaceJob> findByStatus(JobStatus status);

    List<MarketplaceJob> findByClientUserId(UUID clientUserId);
}
