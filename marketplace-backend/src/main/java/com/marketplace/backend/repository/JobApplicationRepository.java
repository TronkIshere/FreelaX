package com.marketplace.backend.repository;

import com.marketplace.backend.entity.JobApplication;
import com.marketplace.backend.entity.JobApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    Optional<JobApplication> findByJobIdAndFreelancerId(UUID jobId, UUID freelancerId);

    List<JobApplication> findByJobId(UUID jobId);

    List<JobApplication> findByJobIdAndStatus(UUID jobId, JobApplicationStatus status);

    List<JobApplication> findByFreelancerIdAndJobIdIn(UUID freelancerId, List<UUID> jobIds);

    @Query("""
            SELECT a FROM JobApplication a
            WHERE a.freelancerId = :freelancerId
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<JobApplication> findMine(@Param("freelancerId") UUID freelancerId,
                                  @Param("status") JobApplicationStatus status,
                                  Pageable pageable);
}
