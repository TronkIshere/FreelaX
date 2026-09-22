package com.marketplace.backend.repository;

import com.marketplace.backend.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByClientUserId(UUID clientUserId);

    List<Job> findByFreelancerUserId(UUID freelancerUserId);

    Page<Job> findByClientUserIdOrFreelancerUserId(UUID clientUserId, UUID freelancerUserId, Pageable pageable);
}
