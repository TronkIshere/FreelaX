package com.marketplace.backend.scheduler;

import com.marketplace.backend.repository.WorkContractRepository;
import com.marketplace.backend.service.AvalancheTaxCertificatePreparer;
import com.marketplace.backend.service.TaxCertificateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Owner decision 2026-11-21: issues the simulated MISA withholding certificate (Decree 117) for released Avalanche escrows. */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@ConditionalOnProperty(name = "avalanche-tax-certificate.enabled", havingValue = "true")
public class AvalancheTaxCertificateScheduler {

    WorkContractRepository contracts;
    AvalancheTaxCertificatePreparer preparer;
    TaxCertificateService taxCertificateService;

    @Scheduled(initialDelayString = "${avalanche-tax-certificate.initial-delay-ms:30000}",
            fixedDelayString = "${avalanche-tax-certificate.interval-ms:30000}")
    public void exportReleasedEscrows() {
        for (UUID contractId : contracts.findReleasedAvalancheWithoutTaxRecord(PageRequest.of(0, 20))) {
            try {
                preparer.prepare(contractId).ifPresent(taxCertificateService::exportForAvalancheRelease);
            } catch (Exception e) {
                log.warn("Avalanche tax certificate will retry for contract {}: {}", contractId, e.getMessage());
            }
        }
    }
}
