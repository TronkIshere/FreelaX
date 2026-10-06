package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.JobCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Job> findWithLockById(UUID id);

    List<Job> findByClientUserId(UUID clientUserId);

    Page<Job> findByClientUserIdOrFreelancerId(UUID userId, UUID userId1, Pageable pageable);

    @Query("""
            SELECT j FROM Job j
            WHERE j.status = com.marketplace.backend.entity.JobStatus.OPEN
              AND j.freelancerId IS NULL
              AND (:keyword IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(COALESCE(j.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:minBudget IS NULL OR j.budgetUsd >= :minBudget)
              AND (:maxBudget IS NULL OR j.budgetUsd <= :maxBudget)
              AND (:category IS NULL OR COALESCE(j.category, com.marketplace.backend.entity.JobCategory.OTHER) = :category)
              AND (:filterSkills = false OR EXISTS (
                   SELECT j2.id FROM Job j2 JOIN j2.skills skill
                   WHERE j2.id = j.id AND LOWER(skill) IN :skills))
              AND (:applicationFilter = 'ALL'
                   OR (:applicationFilter = 'APPLIED' AND EXISTS (
                       SELECT a.id FROM JobApplication a
                       WHERE a.jobId = j.id AND a.freelancerId = :freelancerId))
                   OR (:applicationFilter = 'NOT_APPLIED' AND NOT EXISTS (
                       SELECT a.id FROM JobApplication a
                       WHERE a.jobId = j.id AND a.freelancerId = :freelancerId)))
            """)
    Page<Job> discover(@Param("freelancerId") UUID freelancerId,
                       @Param("keyword") String keyword,
                       @Param("minBudget") BigDecimal minBudget,
                       @Param("maxBudget") BigDecimal maxBudget,
                       @Param("applicationFilter") String applicationFilter,
                       @Param("category") JobCategory category,
                       @Param("filterSkills") boolean filterSkills,
                       @Param("skills") List<String> skills,
                       Pageable pageable);

    List<Job> findByFreelancerIdAndMisaCertificateIdIsNotNull(UUID freelancerId);

    Optional<Job> findByFreelancerIdAndMisaCertificateId(UUID freelancerId, UUID misaCertificateId);
}
