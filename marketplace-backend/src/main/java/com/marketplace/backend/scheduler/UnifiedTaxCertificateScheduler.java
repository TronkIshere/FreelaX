package com.marketplace.backend.scheduler;

import com.marketplace.backend.entity.PaymentFlow;
import com.marketplace.backend.repository.PaymentFlowRepository;
import com.marketplace.backend.service.TaxCertificateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Issues the MISA withholding certificate for unified Jobs after the VND payout is confirmed. */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UnifiedTaxCertificateScheduler {

    PaymentFlowRepository paymentFlows;
    TaxCertificateService taxCertificateService;

    @Scheduled(initialDelayString = "${tax-certificate.unified-initial-delay-ms:30000}",
            fixedDelayString = "${tax-certificate.unified-interval-ms:30000}")
    public void exportPaidFlows() {
        for (PaymentFlow flow : paymentFlows.findPaidWithoutCertificate(PageRequest.of(0, 20))) {
            try {
                taxCertificateService.exportForUnifiedPayout(flow.getId());
            } catch (Exception e) {
                log.warn("Unified tax certificate will retry for flow {}: {}", flow.getId(), e.getMessage());
            }
        }
    }
}
