package com.marketplace.backend.service;

import com.marketplace.backend.client.MisaBackendClient;
import com.marketplace.backend.dto.response.misa.MisaCertificateRecoveryResult;
import com.marketplace.backend.entity.*;
import com.marketplace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/** Runs under the settlement lock. Preparation commits before any remote certificate operation. */
@Service
@RequiredArgsConstructor
public class SettlementTaxService {
    private final TaxCertificateRecordRepository records;
    private final FreelancerPayoutRecordRepository payouts;
    private final JobRepository jobs;
    private final UserRepository users;
    private final MisaBackendClient misa;

    public void prepare(ContractSettlement s) {
        FreelancerPayoutRecord p = payouts.findById(s.getPayoutRecordId()).orElseThrow();
        Job job = jobs.findById(s.getJobId()).orElseThrow();
        if (!Objects.equals(p.getContractSettlementId(), s.getId()) || !p.getJobId().equals(s.getJobId())
                || !p.getFreelancerId().equals(s.getFreelancerId())
                || !p.getClientUserId().equals(job.getClientUserId())
                || !p.getFreelancerId().equals(job.getFreelancerId())
                || p.getAmountUsd().compareTo(s.getAmount()) != 0 || p.getOffRampStatus() != OffRampStatus.COMPLETED) {
            s.setTaxStatus(SettlementStageStatus.FAILED);
            s.setTaxError("TAX_PAYOUT_IDENTITY_CONFLICT");
            return;
        }
        TaxCertificateRecord r = records.findByJobId(s.getJobId()).orElse(null);
        if (r == null) {
            r = new TaxCertificateRecord();
            r.setJobId(s.getJobId()); r.setFreelancerId(p.getFreelancerId()); r.setClientUserId(p.getClientUserId());
            r.setPayoutRecordId(p.getId()); r.setAmountUsd(p.getAmountUsd()); r.setUsdToVndRate(p.getTaxUsdToVndRate());
            r.setRateSource(p.getTaxRateSource()); r.setRateObservedAt(p.getTaxRateObservedAt());
            r.setTaxableIncomeVnd(p.getTaxableAmountVnd());
            // This immutable reference is committed with the quote, not recomputed from changing signatures.
            r.setTransactionReference("settlement:" + s.getId());
        } else if (!Objects.equals(r.getPayoutRecordId(), p.getId())
                || !Objects.equals(r.getFreelancerId(), p.getFreelancerId())
                || !Objects.equals(r.getClientUserId(), p.getClientUserId())
                || !same(r.getAmountUsd(), p.getAmountUsd()) || !same(r.getUsdToVndRate(), p.getTaxUsdToVndRate())
                || !same(r.getTaxableIncomeVnd(), p.getTaxableAmountVnd())) {
            s.setTaxStatus(SettlementStageStatus.FAILED); s.setTaxError("TAX_RECORD_IDENTITY_CONFLICT"); return;
        }
        if (r.getCertificateCreateKey() == null) {
            // Existing exports used the payout-derived default. Preserve that identity when already known.
            r.setCertificateCreateKey(r.getMisaPayoutTransactionId() == null
                    ? "freelax-tax-" + s.getJobId() : "payout-" + r.getMisaPayoutTransactionId());
        }
        records.saveAndFlush(r);
    }

    public void advance(ContractSettlement s) {
        TaxCertificateRecord r = records.findByJobId(s.getJobId()).orElseThrow();
        MisaCertificateRecoveryResult result;
        // Keep local writes outside this catch: a DB rollback must be recovered by the next lookup.
        try {
            result = misa.findCertificateByPlatformPayout(s.getJobId());
        } catch (RuntimeException ex) {
            failure(s, r, SettlementStageStatus.UNKNOWN, "TAX_LOOKUP_UNRESOLVED"); return;
        }
        if (result == null) {
            User u = r.getMisaTaxpayerId() == null ? users.findById(r.getFreelancerId()).orElseThrow() : null;
            try {
                if (r.getMisaTaxpayerId() == null) {
                    r.setMisaTaxpayerId(misa.registerTaxpayerForExternal(u.getId(), u.getDisplayName(),
                            u.getTaxCode(), u.getIdentityNumber(), u.getNationality(), u.getTaxAddress()));
                }
                if (r.getMisaPayoutTransactionId() == null) {
                    r.setMisaPayoutTransactionId(misa.recordPayoutTransaction(r.getMisaTaxpayerId(), r.getJobId(),
                            r.getAmountUsd(), r.getUsdToVndRate(), r.getTransactionReference(), "solana").getId());
                }
                misa.createWithholdingCertificate(r.getMisaPayoutTransactionId(), r.getCertificateCreateKey());
            } catch (HttpClientErrorException ex) {
                int status = ex.getStatusCode().value();
                failure(s, r, status == 429 ? SettlementStageStatus.FAILED_RETRYABLE
                                : status == 408 ? SettlementStageStatus.UNKNOWN : SettlementStageStatus.FAILED,
                        status == 429 ? "TAX_RATE_LIMITED" : status == 408 ? "TAX_CREATE_UNRESOLVED" : "TAX_CREATE_REJECTED");
                return;
            } catch (RuntimeException ex) {
                failure(s, r, SettlementStageStatus.UNKNOWN, "TAX_CREATE_UNRESOLVED"); return;
            }
            try { result = misa.findCertificateByPlatformPayout(s.getJobId()); }
            catch (RuntimeException ex) { failure(s, r, SettlementStageStatus.UNKNOWN, "TAX_LOOKUP_UNRESOLVED"); return; }
        }
        if (!matches(r, result)) {
            failure(s, r, SettlementStageStatus.UNKNOWN, "TAX_RECOVERY_RESPONSE_MISMATCH"); return;
        }
        r.setMisaCertificateId(result.certificateId()); r.setMisaPayoutTransactionId(result.payoutTransactionId());
        r.setCertificateNumber(result.certificateNumber()); r.setCertificateSymbol(result.symbol());
        r.setStatus(TaxCertificateStatus.fromMisa(result.status()).orElseThrow()); r.setMisaStatusRaw(result.status());
        r.setTaxWithheldVnd(result.taxWithheld().setScale(0, RoundingMode.HALF_UP)); r.setIssuedAt(result.issuedAt());
        r.setLastSyncedAt(LocalDateTime.now()); r.setLastError(null); records.save(r);
        Job job = jobs.findById(s.getJobId()).orElseThrow();
        job.setMisaCertificateId(result.certificateId()); job.setMisaPayoutTransactionId(result.payoutTransactionId());
        job.setTaxExportStatus(TaxExportStatus.SUCCESS); jobs.save(job);
        FreelancerPayoutRecord p = payouts.findById(s.getPayoutRecordId()).orElseThrow();
        p.setMisaCertificateId(result.certificateId()); p.setMisaPayoutTransactionId(result.payoutTransactionId()); payouts.save(p);
        // Creation/recovery success is independent of the certificate's later signing/authority status.
        s.setTaxStatus(SettlementStageStatus.SUCCEEDED); s.setTaxReference("certificate:" + result.certificateId()); s.setTaxError(null);
    }

    private boolean matches(TaxCertificateRecord r, MisaCertificateRecoveryResult c) {
        return c != null && c.certificateId() != null && c.payoutTransactionId() != null
                && r.getJobId().toString().equals(c.platformPayoutId()) && c.simulation()
                && (r.getCertificateCreateKey().equals(c.idempotencyKey())
                    // A historical certificate can predate the local key; its payout-derived identity is authoritative.
                    || (r.getMisaCertificateId() == null && r.getMisaPayoutTransactionId() == null
                        && ("payout-" + c.payoutTransactionId()).equals(c.idempotencyKey())))
                && (r.getMisaCertificateId() == null || r.getMisaCertificateId().equals(c.certificateId()))
                && (r.getMisaPayoutTransactionId() == null || r.getMisaPayoutTransactionId().equals(c.payoutTransactionId()))
                && same(r.getAmountUsd(), c.amountUsdc()) && same(r.getUsdToVndRate(), c.exchangeRate())
                && c.taxableIncome() != null
                && same(r.getAmountUsd().multiply(r.getUsdToVndRate()).setScale(2, RoundingMode.HALF_UP), c.taxableIncome())
                && same(r.getTaxableIncomeVnd(), c.taxableIncome().setScale(0, RoundingMode.HALF_UP)) && c.taxWithheld() != null
                && "VND".equals(c.currency()) && TaxCertificateStatus.fromMisa(c.status()).isPresent();
    }

    private boolean same(BigDecimal a, BigDecimal b) { return a != null && b != null && a.compareTo(b) == 0; }

    private void failure(ContractSettlement s, TaxCertificateRecord r, SettlementStageStatus status, String code) {
        s.setTaxStatus(status); s.setTaxError(code); r.setLastError(code); records.save(r);
    }
}
