package com.misa.backend.repository;

import com.misa.backend.entity.WithholdingCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WithholdingCertificateRepository extends JpaRepository<WithholdingCertificate, UUID> {

    Optional<WithholdingCertificate> findByLookupCode(String lookupCode);

    Optional<WithholdingCertificate> findByPayoutTransactionId(UUID payoutTransactionId);

    boolean existsByPayoutTransactionId(UUID payoutTransactionId);

    long countByCertificateNumberIsNotNull();
}
