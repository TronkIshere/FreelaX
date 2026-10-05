package com.marketplace.backend.repository;

import com.marketplace.backend.entity.JobSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobSubmissionRepository extends JpaRepository<JobSubmission, UUID> {
    List<JobSubmission> findByJobIdOrderByVersionAsc(UUID jobId);
    Optional<JobSubmission> findFirstByJobIdOrderByVersionDesc(UUID jobId);
    long countByJobId(UUID jobId);
    Optional<JobSubmission> findByFreelancerIdAndIdempotencyKey(UUID freelancerId, String idempotencyKey);
    Optional<JobSubmission> findFirstByContractIdOrderByVersionDesc(UUID contractId);
    List<JobSubmission> findByContractIdOrderByVersionAsc(UUID contractId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<JobSubmission> findWithLockById(UUID id);

    @Query("select s.milestoneId from JobSubmission s where s.id = :id and s.contractId is not null")
    Optional<UUID> findReviewMilestoneId(@Param("id") UUID id);

    @Query("""
            select s.id from JobSubmission s
            where s.contractId is not null
              and s.status = com.marketplace.backend.entity.JobSubmissionStatus.SUBMITTED
              and s.reviewDueAt <= :now
              and (s.reviewGraceDueAt is null or s.reviewGraceDueAt <= :now)
            order by s.reviewDueAt asc, s.id asc
            """)
    List<UUID> findExpiredReviewIds(@Param("now") Instant now, Pageable pageable);
}
