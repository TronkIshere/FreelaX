package com.marketplace.backend.service.impl;

import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.entity.ExchangeRateSource;
import com.marketplace.backend.entity.ClientPaymentStatus;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.ContractSettlement;
import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.entity.OffRampStatus;
import com.marketplace.backend.entity.OnChainOffRampStatus;
import com.marketplace.backend.entity.OnRampStatus;
import com.marketplace.backend.entity.TaxExportStatus;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.provider.currency.ExchangeRateProvider;
import com.marketplace.backend.provider.currency.ExchangeRateResult;
import com.marketplace.backend.provider.currency.OnRampProvider;
import com.marketplace.backend.provider.currency.OnRampQuote;
import com.marketplace.backend.provider.currency.OnRampResult;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.WalletRepository;
import com.marketplace.backend.service.NotificationService;
import com.marketplace.backend.service.ClientPaymentService;
import com.marketplace.backend.service.OnChainOffRampService;
import com.marketplace.backend.service.PayoutService;
import com.marketplace.backend.service.TaxCertificateService;
import com.marketplace.backend.service.VndPayoutService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PayoutServiceImpl implements PayoutService {

    private static final Locale VI_LOCALE = Locale.forLanguageTag("vi-VN");

    JobRepository jobRepository;
    WalletRepository walletRepository;
    FreelancerPayoutRecordRepository freelancerPayoutRecordRepository;
    OnRampProvider onRampProvider;
    ExchangeRateProvider exchangeRateProvider;
    TaxCertificateService taxCertificateService;
    ClientPaymentService clientPaymentService;
    OnChainOffRampService onChainOffRampService;
    VndPayoutService vndPayoutService;
    NotificationService notificationService;
    SolanaCprProperties solanaCprProperties;

    /** Called under the settlement row lock. Quote/identities commit before any chain mutation. */
    @Transactional
    public FreelancerPayoutRecord prepareContractRecord(Job job, ContractSettlement settlement) {
        FreelancerPayoutRecord prior = freelancerPayoutRecordRepository.findByJobId(job.getId()).orElse(null);
        if (prior != null) {
            if (!settlement.getId().equals(prior.getContractSettlementId())
                    || !settlement.getFreelancerId().equals(prior.getFreelancerId())
                    || settlement.getAmount().compareTo(prior.getAmountUsd()) != 0) {
                throw new ApplicationException(ErrorCode.SETTLEMENT_INELIGIBLE);
            }
            return prior;
        }
        FreelancerPayoutRecord record = createRecord(job);
        record.setContractSettlementId(settlement.getId());
        return freelancerPayoutRecordRepository.saveAndFlush(record);
    }

    @Override
    @Transactional
    public void settle(Job job) {
        if (freelancerPayoutRecordRepository.findByJobId(job.getId()).isPresent()) {
            log.warn("Job {} da co payout record, bo qua settle", job.getId());
            return;
        }

        FreelancerPayoutRecord payoutRecord;
        try {
            payoutRecord = createRecord(job);
        } catch (Exception e) {
            log.error("Khong tao duoc payout record cho job {}: {}", job.getId(), e.getMessage(), e);
            notifyPayoutFailed(job);
            return;
        }

        if (!StringUtils.hasText(payoutRecord.getOnRampClientPublicKey())) {
            applyOnRampResult(payoutRecord, job, OnRampResult.failed(null, null, null,
                    "Client " + job.getClientUserId() + " chua co wallet va chua cau hinh solana-cpr.custodial-client-public-key"));
            return;
        }
        if (!StringUtils.hasText(payoutRecord.getFreelancerPublicKey())) {
            payoutRecord.setClientPaymentStatus(ClientPaymentStatus.FAILED);
            payoutRecord.setClientPaymentError("Freelancer " + job.getFreelancerId() + " chua co wallet Solana");
            freelancerPayoutRecordRepository.save(payoutRecord);
            notifyPayoutFailed(job);
            return;
        }

        runOnRamp(payoutRecord, job);
    }

    @Override
    @Transactional
    public void reconcile(UUID payoutRecordId) {
        FreelancerPayoutRecord payoutRecord = freelancerPayoutRecordRepository.findById(payoutRecordId).orElse(null);
        if (payoutRecord == null) {
            return;
        }
        if (payoutRecord.getContractSettlementId() != null) return;
        Job job = jobRepository.findById(payoutRecord.getJobId()).orElse(null);
        if (job == null) {
            return;
        }
        if (payoutRecord.getOffRampStatus() == OffRampStatus.COMPLETED) {
            if (job.getTaxExportStatus() == TaxExportStatus.NOT_ATTEMPTED) {
                exportTaxIfMissing(job, payoutRecord);
            }
            return;
        }
        if (payoutRecord.getClientPaymentStatus() == null) {
            payoutRecord.setClientPaymentStatus(ClientPaymentStatus.NOT_STARTED);
            freelancerPayoutRecordRepository.save(payoutRecord);
        }
        if (payoutRecord.getOnChainOffRampStatus() == null) {
            payoutRecord.setOnChainOffRampStatus(OnChainOffRampStatus.NOT_STARTED);
            freelancerPayoutRecordRepository.save(payoutRecord);
        }

        switch (payoutRecord.getOnRampStatus()) {
            case NOT_STARTED -> {
                if (isExpired(payoutRecord.getCreatedAt())) {
                    applyOnRampResult(payoutRecord, job, OnRampResult.failed(null, null, null,
                            "Qua " + solanaCprProperties.getPendingExpirySeconds()
                                    + " giay van chua gui duoc mock_onramp: " + payoutRecord.getOnRampError()));
                } else {
                    runOnRamp(payoutRecord, job);
                }
            }
            case SUBMITTED -> applyOnRampResult(payoutRecord, job, onRampProvider.resume(
                    toQuote(payoutRecord),
                    payoutRecord.getOnRampClientPublicKey(),
                    payoutRecord.getOnRampTransactionSignature(),
                    payoutRecord.getOnRampClientUsdcAta(),
                    payoutRecord.getOnRampReceiptPda(),
                    payoutRecord.getOnRampSubmittedAt()));
            case CONFIRMED -> {
                if (payoutRecord.getClientPaymentStatus() != ClientPaymentStatus.CONFIRMED
                        && payoutRecord.getClientPaymentStatus() != ClientPaymentStatus.FAILED) {
                    advanceClientPayment(payoutRecord, job);
                } else if (payoutRecord.getClientPaymentStatus() == ClientPaymentStatus.CONFIRMED) {
                    if (payoutRecord.getOnChainOffRampStatus() == OnChainOffRampStatus.CONFIRMED) {
                        if (payoutRecord.getOffRampStatus() != OffRampStatus.COMPLETED
                                && payoutRecord.getOffRampStatus() != OffRampStatus.FAILED) {
                            advanceVndPayout(payoutRecord, job);
                        }
                    } else if (payoutRecord.getOnChainOffRampStatus() != OnChainOffRampStatus.FAILED) {
                        advanceOnChainOffRamp(payoutRecord, job);
                    }
                }
            }
            default -> {
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findRecordIdsToReconcile() {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        freelancerPayoutRecordRepository
                .findByOnRampStatusIn(List.of(OnRampStatus.NOT_STARTED, OnRampStatus.SUBMITTED))
                .stream().filter(r -> r.getContractSettlementId() == null)
                .forEach(r -> ids.add(r.getId()));
        freelancerPayoutRecordRepository.findByOnRampStatus(OnRampStatus.CONFIRMED).stream()
                .filter(r -> r.getContractSettlementId() == null)
                .filter(r -> (r.getOffRampStatus() == OffRampStatus.COMPLETED
                        && r.getMisaCertificateId() == null
                        && jobRepository.findById(r.getJobId())
                        .map(j -> j.getTaxExportStatus() == TaxExportStatus.NOT_ATTEMPTED).orElse(false))
                        || r.getClientPaymentStatus() == null
                        || (r.getClientPaymentStatus() != ClientPaymentStatus.FAILED
                        && (r.getClientPaymentStatus() != ClientPaymentStatus.CONFIRMED
                        || r.getOnChainOffRampStatus() == null
                        || r.getOnChainOffRampStatus() == OnChainOffRampStatus.NOT_STARTED
                        || r.getOnChainOffRampStatus() == OnChainOffRampStatus.REQUEST_SUBMITTED
                        || (r.getOnChainOffRampStatus() == OnChainOffRampStatus.CONFIRMED
                        && r.getOffRampStatus() != OffRampStatus.COMPLETED
                        && r.getOffRampStatus() != OffRampStatus.FAILED))))
                .forEach(r -> ids.add(r.getId()));
        return new ArrayList<>(ids);
    }

    private FreelancerPayoutRecord createRecord(Job job) {
        if (job.getFreelancerId() == null) {
            throw new ApplicationException(ErrorCode.JOB_FREELANCER_NOT_ASSIGNED, job.getId());
        }

        String recipientPublicKey = resolveRecipientPublicKey(job.getClientUserId());

        ExchangeRateResult taxRate = exchangeRateProvider.getUsdToVndRate();
        if (taxRate.source() == ExchangeRateSource.FALLBACK_PLACEHOLDER) {
            log.warn("Job {}: so khai thue dang dung ty gia USD/VND FALLBACK PLACEHOLDER ({})", job.getId(), taxRate.rate());
        }
        BigDecimal storedTaxRate = taxRate.rate().setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableAmountVnd = job.getBudgetUsd()
                .multiply(storedTaxRate)
                .setScale(0, RoundingMode.HALF_UP);

        OnRampQuote quote = onRampProvider.quote(job.getId(), job.getBudgetUsd());

        FreelancerPayoutRecord payoutRecord = new FreelancerPayoutRecord();
        payoutRecord.setJobId(job.getId());
        payoutRecord.setFreelancerId(job.getFreelancerId());
        payoutRecord.setClientUserId(job.getClientUserId());
        payoutRecord.setSimulated(true);

        payoutRecord.setAmountUsd(quote.amountUsdSource());
        payoutRecord.setOnRampFeeUsd(quote.feeUsd());
        payoutRecord.setAmountUsdNet(quote.amountUsdNet());
        payoutRecord.setOnRampUsdAmountE6(quote.usdAmountE6());
        payoutRecord.setOnRampPurchaseId(quote.purchaseId());
        payoutRecord.setOnRampClientPublicKey(recipientPublicKey);
        payoutRecord.setFreelancerPublicKey(resolveWalletPublicKey(job.getFreelancerId()));
        payoutRecord.setOnRampNetwork(onRampProvider.network());
        payoutRecord.setOnRampStatus(OnRampStatus.NOT_STARTED);
        payoutRecord.setOnChainOffRampStatus(OnChainOffRampStatus.NOT_STARTED);
        payoutRecord.setOffRampStatus(OffRampStatus.NOT_STARTED);

        payoutRecord.setTaxUsdToVndRate(storedTaxRate);
        payoutRecord.setTaxRateSource(taxRate.source());
        payoutRecord.setTaxRateObservedAt(taxRate.fetchedAt());
        payoutRecord.setTaxableAmountVnd(taxableAmountVnd);

        return freelancerPayoutRecordRepository.save(payoutRecord);
    }

    private String resolveRecipientPublicKey(UUID clientUserId) {
        return walletRepository.findFirstByUserIdOrderByIdAsc(clientUserId)
                .map(Wallet::getPublicKey)
                .filter(StringUtils::hasText)
                .orElseGet(() -> StringUtils.hasText(solanaCprProperties.getCustodialClientPublicKey())
                        ? solanaCprProperties.getCustodialClientPublicKey()
                        : null);
    }

    private String resolveWalletPublicKey(UUID userId) {
        return walletRepository.findFirstByUserIdOrderByIdAsc(userId)
                .map(Wallet::getPublicKey)
                .filter(StringUtils::hasText)
                .orElse(null);
    }

    private void runOnRamp(FreelancerPayoutRecord payoutRecord, Job job) {
        OnRampResult result;
        try {
            result = onRampProvider.execute(toQuote(payoutRecord), payoutRecord.getOnRampClientPublicKey());
        } catch (Exception e) {
            result = OnRampResult.failed(null, null, null, e.getMessage());
        }
        applyOnRampResult(payoutRecord, job, result);
    }

    private void applyOnRampResult(FreelancerPayoutRecord payoutRecord, Job job, OnRampResult result) {
        payoutRecord.setOnRampStatus(result.status());
        payoutRecord.setOnRampError(result.error());
        if (StringUtils.hasText(result.transactionSignature())) {
            payoutRecord.setOnRampTransactionSignature(result.transactionSignature());
        }
        if (StringUtils.hasText(result.clientUsdcAta())) {
            payoutRecord.setOnRampClientUsdcAta(result.clientUsdcAta());
        }
        if (StringUtils.hasText(result.receiptPda())) {
            payoutRecord.setOnRampReceiptPda(result.receiptPda());
        }
        if (result.status() == OnRampStatus.SUBMITTED && payoutRecord.getOnRampSubmittedAt() == null) {
            payoutRecord.setOnRampSubmittedAt(LocalDateTime.now());
        }
        if (result.status() == OnRampStatus.CONFIRMED) {
            payoutRecord.setAmountUsdcReceived(result.amountUsdcReceived());
            payoutRecord.setOnRampConfirmedAt(LocalDateTime.now());
        }
        freelancerPayoutRecordRepository.save(payoutRecord);

        switch (result.status()) {
            case CONFIRMED -> advanceClientPayment(payoutRecord, job);
            case FAILED -> {
                log.error("Mock on-ramp that bai cho job {}: {}", job.getId(), result.error());
                notifyPayoutFailed(job);
            }
            case SUBMITTED -> log.info("Job {}: mock on-ramp dang cho xac nhan ({})", job.getId(), result.error());
            case NOT_STARTED -> log.warn("Job {}: mock on-ramp chua gui duoc, se thu lai ({})", job.getId(), result.error());
        }
    }

    private void advanceClientPayment(FreelancerPayoutRecord payoutRecord, Job job) {
        clientPaymentService.advance(payoutRecord);
        if (payoutRecord.getClientPaymentStatus() == ClientPaymentStatus.FAILED) {
            notifyPayoutFailed(job);
        }
    }

    private void advanceOnChainOffRamp(FreelancerPayoutRecord payoutRecord, Job job) {
        onChainOffRampService.advance(payoutRecord);
        if (payoutRecord.getOnChainOffRampStatus() == OnChainOffRampStatus.FAILED) {
            notifyPayoutFailed(job);
        }
    }

    private void advanceVndPayout(FreelancerPayoutRecord payoutRecord, Job job) {
        OffRampStatus previousStatus = payoutRecord.getOffRampStatus();
        vndPayoutService.advance(payoutRecord);
        if (previousStatus == OffRampStatus.NOT_STARTED
                && payoutRecord.getOffRampStatus() == OffRampStatus.SIMULATED) {
            notifyPayoutSimulated(job, payoutRecord);
        } else if (payoutRecord.getOffRampStatus() == OffRampStatus.COMPLETED) {
            exportTaxIfMissing(job, payoutRecord);
        } else if (payoutRecord.getOffRampStatus() == OffRampStatus.FAILED) {
            notifyPayoutFailed(job);
        }
    }

    private void exportTaxIfMissing(Job job, FreelancerPayoutRecord payoutRecord) {
        if (payoutRecord.getOffRampStatus() == OffRampStatus.COMPLETED
                && job.getMisaCertificateId() == null) {
            taxCertificateService.exportForPayout(job, payoutRecord, onChainReference(payoutRecord));
        }
    }

    private void notifyPayoutSimulated(Job job, FreelancerPayoutRecord payoutRecord) {
        String message = "Công việc \"" + job.getTitle() + "\" đã hoàn tất. "
                + "USDC đã được chuyển on-chain theo luồng Client → Freelancer → Treasury với số lượng "
                + payoutRecord.getAmountUsdcReceived().stripTrailingZeros().toPlainString()
                + " Mock USDC trên " + payoutRecord.getOnRampNetwork()
                + " (" + onChainReference(payoutRecord) + "). "
                + "Số tiền VND dự kiến nhận sau phí là " + formatVnd(payoutRecord.getAmountVndEstimated())
                + " VNĐ, tính theo tỷ giá USDC/VND "
                + rateLabel(payoutRecord.getUsdcToVndRate(), payoutRecord.getUsdcToVndRateSource())
                + ". Đây là off-ramp mô phỏng, chưa có chuyển khoản ngân hàng thật. "
                + "Thu nhập chịu thuế ghi nhận " + formatVnd(payoutRecord.getTaxableAmountVnd())
                + " VNĐ, quy đổi từ " + payoutRecord.getAmountUsd().stripTrailingZeros().toPlainString()
                + " USD theo tỷ giá USD/VND "
                + rateLabel(payoutRecord.getTaxUsdToVndRate(), payoutRecord.getTaxRateSource()) + ".";

        notificationService.notify(
                payoutRecord.getFreelancerId(),
                NotificationType.PAYMENT_RECEIVED,
                "Hoàn tất mô phỏng payout",
                message,
                job.getId(),
                payoutRecord.getAmountVndEstimated());
    }

    private void notifyPayoutFailed(Job job) {
        notificationService.notify(
                job.getFreelancerId(),
                NotificationType.PAYOUT_FAILED,
                "Chuyển đổi thanh toán thất bại",
                "Công việc \"" + job.getTitle() + "\" đã hoàn tất nhưng quá trình chuyển đổi USD → USDC → VNĐ "
                        + "thất bại, hệ thống sẽ cần xử lý lại thủ công.",
                job.getId());
    }

    private String onChainReference(FreelancerPayoutRecord payoutRecord) {
        if (StringUtils.hasText(payoutRecord.getOffRampCompletionSignature())) {
            return payoutRecord.getOffRampCompletionSignature();
        }
        if (StringUtils.hasText(payoutRecord.getWithdrawalTransactionSignature())) {
            return payoutRecord.getWithdrawalTransactionSignature();
        }
        if (StringUtils.hasText(payoutRecord.getPaymentTransactionSignature())) {
            return payoutRecord.getPaymentTransactionSignature();
        }
        if (StringUtils.hasText(payoutRecord.getOnRampTransactionSignature())) {
            return payoutRecord.getOnRampTransactionSignature();
        }
        if (StringUtils.hasText(payoutRecord.getOnRampReceiptPda())) {
            return "receipt:" + payoutRecord.getOnRampReceiptPda();
        }
        return "purchase:" + payoutRecord.getOnRampPurchaseId();
    }

    private String rateLabel(BigDecimal rate, ExchangeRateSource source) {
        String value = formatVnd(rate);
        if (source == ExchangeRateSource.FALLBACK_PLACEHOLDER) {
            return value + " (tỷ giá giả lập placeholder)";
        }
        if (source == ExchangeRateSource.LIVE_COINGECKO) {
            return value + " (tham chiếu CoinGecko)";
        }
        if (source == ExchangeRateSource.LIVE_OPEN_ER_API) {
            return value + " (tham chiếu open.er-api.com)";
        }
        return value;
    }

    private OnRampQuote toQuote(FreelancerPayoutRecord payoutRecord) {
        return new OnRampQuote(
                payoutRecord.getAmountUsd(),
                payoutRecord.getOnRampFeeUsd(),
                payoutRecord.getAmountUsdNet(),
                payoutRecord.getOnRampUsdAmountE6(),
                payoutRecord.getOnRampPurchaseId());
    }

    private boolean isExpired(LocalDateTime since) {
        return since != null && since.plusSeconds(solanaCprProperties.getPendingExpirySeconds()).isBefore(LocalDateTime.now());
    }

    private String formatVnd(BigDecimal amount) {
        return String.format(VI_LOCALE, "%,d", amount.setScale(0, RoundingMode.HALF_UP).longValueExact());
    }
}
