package com.marketplace.backend.repository;

import com.marketplace.backend.entity.ContractReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractReviewRepository extends JpaRepository<ContractReview, UUID> {
    @Query("select r.contractId from ContractReview r where r.id = :reviewId")
    Optional<UUID> findContractId(@Param("reviewId") UUID reviewId);
    Optional<ContractReview> findByContractIdAndReviewerId(UUID contractId, UUID reviewerId);
    List<ContractReview> findByContractIdOrderByCreatedAtAsc(UUID contractId);
    @Query("select r.overall from ContractReview r where r.revieweeId = :userId and r.publishedAt is not null and r.invalidatedAt is null")
    List<Integer> findPublishedScores(@Param("userId") UUID userId);
    @Query("select r from ContractReview r where r.revieweeId = :userId and r.publishedAt is not null and r.invalidatedAt is null order by r.publishedAt desc")
    List<ContractReview> findPublicForUser(@Param("userId") UUID userId, Pageable page);
    @Query("select distinct r.contractId from ContractReview r where r.submittedAt is not null and r.publishedAt is null and r.completedAt <= :cutoff order by r.contractId")
    List<UUID> findDuePublication(@Param("cutoff") Instant cutoff, Pageable page);
    List<ContractReview> findByReportedAtIsNotNullAndHiddenAtIsNullAndInvalidatedAtIsNullOrderByReportedAtAsc(Pageable page);
}
