package com.marketplace.backend.repository;

import com.marketplace.backend.entity.TaxCertificateRecord;
import com.marketplace.backend.entity.TaxCertificateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxCertificateRecordRepository extends JpaRepository<TaxCertificateRecord, UUID> {

    Optional<TaxCertificateRecord> findByJobId(UUID jobId);

    Page<TaxCertificateRecord> findByFreelancerIdOrClientUserId(UUID freelancerId, UUID clientUserId, Pageable pageable);

    List<TaxCertificateRecord> findByStatusIn(Collection<TaxCertificateStatus> statuses);
}