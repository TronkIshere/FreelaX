package com.misa.backend.repository;

import com.misa.backend.entity.Taxpayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaxpayerRepository extends JpaRepository<Taxpayer, UUID> {

    Optional<Taxpayer> findByUserId(UUID userId);

    boolean existsByTaxCode(String taxCode);
}
