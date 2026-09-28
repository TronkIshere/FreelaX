package com.marketplace.backend.service.impl;

import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.entity.ExchangeRateSource;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.entity.OffRampStatus;
import com.marketplace.backend.entity.OnRampStatus;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.provider.currency.ExchangeRateProvider;
import com.marketplace.backend.provider.currency.ExchangeRateResult;
import com.marketplace.backend.provider.currency.OffRampProvider;
import com.marketplace.backend.provider.currency.OffRampResult;
import com.marketplace.backend.provider.currency.OnRampProvider;
import com.marketplace.backend.provider.currency.OnRampQuote;
import com.marketplace.backend.provider.currency.OnRampResult;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.WalletRepository;
import com.marketplace.backend.service.NotificationService;
import com.marketplace.backend.service.PayoutService;
import com.marketplace.backend.service.TaxCertificateService;
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
    OffRampProvider offRampProvider;
    ExchangeRateProvider exchangeRateProvider;
    TaxCertificateService taxCertificateService;
    NotificationService notificationService;
    SolanaCprProperties solanaCprProperties;

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

        runOnRamp(payoutRecord, job);
    }

    @Override
    @Transactional
    public void reconcile(UUID payoutRecordId) {
        FreelancerPayoutRecord payoutRecord = freelancerPayoutRecordRepository.findById(payoutRecordId).orElse(null);
        if (payoutRecord == null) {
            return;
        }
        Job job = jobRepository.findById(payoutRecord.getJobId()).orElse(null);
        if (job == null) {
            return;
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
                if (payoutRecord.getOffRampStatus() == OffRampStatus.NOT_STARTED) {
                    completePayout(payoutRecord, job);
                }
            }
            default -> {
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findRecordIdsToReconcile() {
        List<UUID> ids = new ArrayList<>();
        freelancerPayoutRecordRepository
                .findByOnRampStatusIn(List.of(OnRampStatus.NOT_STARTED, OnRampStatus.SUBMITTED))
                .forEach(r -> ids.add(r.getId()));
        freelancerPayoutRecordRepository
                .findByOnRampStatusAndOffRampStatus(OnRampStatus.CONFIRMED, OffRampStatus.NOT_STARTED)
                .forEach(r -> ids.add(r.getId()));
        return ids;
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
        BigDecimal taxableAmountVnd = job.getBudgetUsd()
                .multiply(taxRate.rate())
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
        payoutRecord.setOnRampNetwork(onRampProvider.network());
        payoutRecord.setOnRampStatus(OnRampStatus.NOT_STARTED);
        payoutRecord.setOffRampStatus(OffRampStatus.NOT_STARTED);

        payoutRecord.setTaxUsdToVndRate(taxRate.rate());
        payoutRecord.setTaxRateSource(taxRate.source());
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
            case CONFIRMED -> completePayout(payoutRecord, job);
            case FAILED -> {
                log.error("Mock on-ramp that bai cho job {}: {}", job.getId(), result.error());
                notifyPayoutFailed(job);
                exportTaxIfMissing(job, payoutRecord);
            }
            case SUBMITTED -> log.info("Job {}: mock on-ramp dang cho xac nhan ({})", job.getId(), result.error());
            case NOT_STARTED -> log.warn("Job {}: mock on-ramp chua gui duoc, se thu lai ({})", job.getId(), result.error());
        }
    }

    private void completePayout(FreelancerPayoutRecord payoutRecord, Job job) {
        OffRampResult offRamp;
        try {
            offRamp = offRampProvider.convertUsdcToVnd(job.getId(), payoutRecord.getAmountUsdcReceived());
        } catch (Exception e) {
            payoutRecord.setOffRampStatus(OffRampStatus.FAILED);
            freelancerPayoutRecordRepository.save(payoutRecord);
            log.error("Mo phong off-ramp that bai cho job {}: {}", job.getId(), e.getMessage(), e);
            notifyPayoutFailed(job);
            exportTaxIfMissing(job, payoutRecord);
            return;
        }

        if (offRamp.rateSource() == ExchangeRateSource.FALLBACK_PLACEHOLDER) {
            log.warn("Job {}: off-ramp dang dung ty gia USDC/VND FALLBACK PLACEHOLDER ({})", job.getId(), offRamp.usdcToVndRate());
        }

        payoutRecord.setUsdcToVndRate(offRamp.usdcToVndRate());
        payoutRecord.setUsdcToVndRateSource(offRamp.rateSource());
        payoutRecord.setAmountVndBeforeOffRampFee(offRamp.amountVndGross());
        payoutRecord.setOffRampFeeVnd(offRamp.feeVnd());
        payoutRecord.setAmountVndEstimated(offRamp.amountVndNet());
        payoutRecord.setOffRampReference(offRamp.payoutReference());
        payoutRecord.setOffRampStatus(OffRampStatus.SIMULATED);
        freelancerPayoutRecordRepository.save(payoutRecord);

        notifyPayoutSimulated(job, payoutRecord);
        exportTaxIfMissing(job, payoutRecord);
    }

    private void exportTaxIfMissing(Job job, FreelancerPayoutRecord payoutRecord) {
        if (job.getMisaCertificateId() == null) {
            taxCertificateService.exportForPayout(job, payoutRecord, onChainReference(payoutRecord));
        }
    }

    private void notifyPayoutSimulated(Job job, FreelancerPayoutRecord payoutRecord) {
        String message = "Công việc \"" + job.getTitle() + "\" đã hoàn tất. Đã hoàn tất mô phỏng payout: cấp "
                + payoutRecord.getAmountUsdcReceived().stripTrailingZeros().toPlainString()
                + " Mock USDC on-chain trên " + payoutRecord.getOnRampNetwork()
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