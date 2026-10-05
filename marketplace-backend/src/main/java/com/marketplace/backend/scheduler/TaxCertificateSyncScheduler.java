package com.marketplace.backend.scheduler;

import com.marketplace.backend.service.TaxCertificateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaxCertificateSyncScheduler {

    TaxCertificateService taxCertificateService;

    @Scheduled(initialDelayString = "${tax-certificate.sync-initial-delay-ms:60000}",
            fixedDelayString = "${tax-certificate.sync-interval-ms:300000}")
    public void syncCertificateStatuses() {
        for (UUID taxRecordId : taxCertificateService.findRecordIdsToSync()) {
            try {
                taxCertificateService.syncById(taxRecordId);
            } catch (Exception e) {
                log.warn("Tax certificate sync will retry for record {}", taxRecordId);
            }
        }
    }
}
