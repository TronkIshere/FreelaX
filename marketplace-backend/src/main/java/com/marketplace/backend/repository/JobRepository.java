package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByClientUserId(UUID clientUserId);

    Page<Job> findByClientUserIdOrFreelancerId(UUID userId, UUID userId1, Pageable pageable);

    List<Job> findByFreelancerIdAndMisaCertificateIdIsNotNull(UUID freelancerId);

    Optional<Job> findByFreelancerIdAndMisaCertificateId(UUID freelancerId, UUID misaCertificateId);
}