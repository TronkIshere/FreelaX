package com.marketplace.backend.repository;

import com.marketplace.backend.entity.JobApplication;
import com.marketplace.backend.entity.JobApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    Optional<JobApplication> findByJobIdAndFreelancerId(UUID jobId, UUID freelancerId);

    List<JobApplication> findByJobId(UUID jobId);

    List<JobApplication> findByJobIdAndStatus(UUID jobId, JobApplicationStatus status);
}