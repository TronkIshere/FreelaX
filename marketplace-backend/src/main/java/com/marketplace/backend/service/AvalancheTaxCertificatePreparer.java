package com.marketplace.backend.service;

import com.marketplace.backend.entity.ContractStatus;
import com.marketplace.backend.entity.Milestone;
import com.marketplace.backend.entity.TaxCertificateRecord;
import com.marketplace.backend.entity.TaxCertificateStatus;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.payout.avalanche.SolanaRailFeeTaxRules;
import com.marketplace.backend.provider.currency.ExchangeRateProvider;
import com.marketplace.backend.provider.currency.ExchangeRateResult;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.TaxCertificateRecordRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import com.marketplace.backend.service.impl.TaxCertificateServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Owner decision 2026-11-21: a released Avalanche escrow gets a simulated MISA withholding certificate under Decree 117/2025
 * (the platform withholds on behalf of the individual provider: PIT 2% + VAT 5% of the service revenue; MISA computes both).
 * The revenue is the escrowed amount the freelancer received on chain (USDC, 1:1 USD); it is converted to VND with the same
 * USD/VND rate provider and rounding the Solana rail uses ({@link SolanaRailFeeTaxRules}). This only records accounting figures:
 * the USDC already in the freelancer's wallet is never changed, and nothing here moves funds.
 */
@Service
@RequiredArgsConstructor
public class AvalancheTaxCertificatePreparer {

    public static final String SCHEME = "VN_DECREE_117_SERVICES";

    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final TaxCertificateRecordRepository records;
    private final ExchangeRateProvider exchangeRates;

    /** Creates (once) the record of a released Avalanche contract; returns its id, or empty when the contract is not eligible. */
    @Transactional
    public Optional<UUID> prepare(UUID contractId) {
        WorkContract contract = contracts.findById(contractId).orElse(null);
        if (contract == null || contract.getStatus() != ContractStatus.COMPLETED
                || !TaxRecordBlockchain.AVALANCHE.equals(TaxRecordBlockchain.label(contract.getPaymentNetworkId()))) {
            return Optional.empty();
        }
        Optional<TaxCertificateRecord> existing = records.findByJobId(contract.getJobId());
        if (existing.isPresent()) {
            String reference = existing.get().getTransactionReference();
            return reference != null && reference.equals(reference(contractId)) ? Optional.of(existing.get().getId()) : Optional.empty();
        }
        BigDecimal amount = milestones.findByContractId(contractId).map(Milestone::getAmount).orElse(contract.getBudgetUsd());
        if (amount == null || amount.signum() <= 0) return Optional.empty();
        ExchangeRateResult rate = exchangeRates.getUsdToVndRate();
        BigDecimal stored = SolanaRailFeeTaxRules.storedTaxRate(rate.rate());
        TaxCertificateRecord r = new TaxCertificateRecord();
        r.setJobId(contract.getJobId());
        r.setFreelancerId(contract.getFreelancerId());
        r.setClientUserId(contract.getClientUserId());
        r.setAmountUsd(amount.setScale(2, RoundingMode.HALF_UP));
        r.setUsdToVndRate(stored);
        r.setRateSource(rate.source());
        r.setRateObservedAt(rate.fetchedAt());
        r.setTaxableIncomeVnd(SolanaRailFeeTaxRules.taxableAmountVnd(r.getAmountUsd(), stored));
        r.setTransactionReference(reference(contractId));
        r.setTaxScheme(SCHEME);
        r.setStatus(TaxCertificateStatus.PENDING_EXPORT);
        return Optional.of(records.saveAndFlush(r).getId());
    }

    static String reference(UUID contractId) {
        return TaxCertificateServiceImpl.AVALANCHE_RELEASE_REFERENCE + contractId;
    }
}
