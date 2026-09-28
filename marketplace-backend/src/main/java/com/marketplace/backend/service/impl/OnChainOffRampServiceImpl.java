package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.request.solana.RequestOfframpRequest;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaOperationResult;
import com.marketplace.backend.dto.response.solana.SolanaTransactionStatusResult;
import com.marketplace.backend.dto.response.solana.SolanaWithdrawalResult;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.OnChainOffRampStatus;
import com.marketplace.backend.exception.SolanaCprException;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.service.OnChainOffRampService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnChainOffRampServiceImpl implements OnChainOffRampService {

    private static final int USDC_DECIMALS = 6;
    private static final String SEND_MODE = "send";
    private static final String PENDING = "Pending";
    private static final String COMPLETED = "Completed";
    private static final String FAILED_PENDING_REVIEW = "FailedPendingReview";

    private final SolanaCprClient solanaCprClient;
    private final SolanaCprProperties properties;
    private final FreelancerPayoutRecordRepository payoutRecordRepository;

    @Override
    @Transactional
    public void advance(FreelancerPayoutRecord record) {
        OnChainOffRampStatus status = record.getOnChainOffRampStatus() == null
                ? OnChainOffRampStatus.NOT_STARTED : record.getOnChainOffRampStatus();
        if (status == OnChainOffRampStatus.CONFIRMED || status == OnChainOffRampStatus.FAILED) {
            return;
        }
        if (record.getOnChainOffRampStatus() == null) {
            record.setOnChainOffRampStatus(status);
            payoutRecordRepository.save(record);
        }
        try {
            if (status == OnChainOffRampStatus.NOT_STARTED) {
                requestOrConfirm(record);
            } else if (status == OnChainOffRampStatus.REQUEST_SUBMITTED) {
                confirm(record);
            }
        } catch (SolanaCprException exception) {
            handleGatewayFailure(record, exception);
        } catch (RuntimeException exception) {
            fail(record, exception.getMessage());
        }
    }

    private void requestOrConfirm(FreelancerPayoutRecord record) {
        initializeWithdrawal(record);
        Optional<SolanaWithdrawalResult> existing = solanaCprClient.findWithdrawal(
                record.getFreelancerPublicKey(), record.getWithdrawalId());
        if (existing.isPresent()) {
            applyWithdrawal(record, existing.get());
            return;
        }
        if (record.getInvoiceExpiresAtEpoch() == null
                || record.getInvoiceExpiresAtEpoch() <= Instant.now().getEpochSecond()) {
            fail(record, "RateSnapshot da het han truoc khi request_offramp");
            return;
        }
        SolanaConfigResult config = solanaCprClient.getConfig();
        if (!Objects.equals(record.getPaymentMint(), config.getAcceptedMint())
                || !StringUtils.hasText(config.getTreasuryAuthority())) {
            fail(record, "Config mint/Treasury Authority khong khop Client payment");
            return;
        }
        record.setTreasuryPublicKey(config.getTreasuryAuthority());

        SolanaOperationResult submitted = solanaCprClient.requestOfframp(RequestOfframpRequest.builder()
                .freelancer(record.getFreelancerPublicKey())
                .withdrawalId(record.getWithdrawalId())
                .rateId(record.getRateId())
                .tokenAmount(record.getWithdrawalTokenAmount())
                .mode(SEND_MODE)
                .commitment(properties.getCommitment())
                .skipPreflight(false)
                .build());

        record.setWithdrawalTransactionSignature(submitted.getSignature());
        if (submitted.getDerivedAccounts() != null) {
            record.setWithdrawalPda(submitted.getDerivedAccounts().getWithdrawalRecord());
            record.setTreasuryUsdcAta(submitted.getDerivedAccounts().getTreasuryAta());
        }
        record.setOnChainOffRampStatus(OnChainOffRampStatus.REQUEST_SUBMITTED);
        record.setWithdrawalSubmittedAt(LocalDateTime.now());
        record.setOnChainOffRampError(null);
        payoutRecordRepository.save(record);
    }

    private void confirm(FreelancerPayoutRecord record) {
        Optional<SolanaWithdrawalResult> withdrawal = solanaCprClient.findWithdrawal(
                record.getFreelancerPublicKey(), record.getWithdrawalId());
        if (withdrawal.isPresent()) {
            applyWithdrawal(record, withdrawal.get());
            return;
        }

        if (StringUtils.hasText(record.getWithdrawalTransactionSignature())) {
            SolanaTransactionStatusResult transaction = solanaCprClient.getTransactionStatus(
                    record.getWithdrawalTransactionSignature());
            if (transaction.hasError()) {
                fail(record, "request_offramp loi on-chain: " + transaction.getError());
                return;
            }
        }
        if (isExpired(record.getWithdrawalSubmittedAt())) {
            fail(record, "Khong tim thay WithdrawalRecord sau " + properties.getPendingExpirySeconds() + " giay");
        } else {
            record.setOnChainOffRampError("Dang cho WithdrawalRecord duoc xac nhan");
            payoutRecordRepository.save(record);
        }
    }

    private void applyWithdrawal(FreelancerPayoutRecord record, SolanaWithdrawalResult withdrawal) {
        SolanaConfigResult config = solanaCprClient.getConfig();
        if (!sameInteger(withdrawal.getWithdrawalId(), record.getWithdrawalId())
                || !Objects.equals(withdrawal.getFreelancer(), record.getFreelancerPublicKey())
                || !sameInteger(withdrawal.getTokenAmount(), record.getWithdrawalTokenAmount())
                || !Objects.equals(withdrawal.getMint(), record.getPaymentMint())
                || !Objects.equals(withdrawal.getMint(), config.getAcceptedMint())
                || !StringUtils.hasText(config.getTreasuryAuthority())
                || (StringUtils.hasText(record.getTreasuryUsdcAta())
                && !Objects.equals(withdrawal.getTreasury(), record.getTreasuryUsdcAta()))
                || !Objects.equals(withdrawal.getRateSnapshot(), record.getRateSnapshotPda())) {
            throw new IllegalStateException("WithdrawalRecord on-chain khong khop payout record");
        }
        if (FAILED_PENDING_REVIEW.equals(withdrawal.getStatus())) {
            fail(record, "Withdrawal dang FailedPendingReview");
            return;
        }
        if (!PENDING.equals(withdrawal.getStatus()) && !COMPLETED.equals(withdrawal.getStatus())) {
            fail(record, "Withdrawal co trang thai khong hop le: " + withdrawal.getStatus());
            return;
        }

        record.setWithdrawalPda(withdrawal.getAddress());
        if (!StringUtils.hasText(record.getTreasuryPublicKey())) {
            record.setTreasuryPublicKey(config.getTreasuryAuthority());
        }
        record.setTreasuryUsdcAta(withdrawal.getTreasury());
        record.setWithdrawalFiatAmountVnd(withdrawal.getFiatAmountVnd());
        record.setOnChainOffRampStatus(OnChainOffRampStatus.CONFIRMED);
        record.setWithdrawalConfirmedAt(LocalDateTime.now());
        record.setOnChainOffRampError(null);
        payoutRecordRepository.save(record);
    }

    private void initializeWithdrawal(FreelancerPayoutRecord record) {
        if (!StringUtils.hasText(record.getWithdrawalId())) {
            record.setWithdrawalId(Long.toUnsignedString(record.getJobId().getLeastSignificantBits()));
        }
        if (!StringUtils.hasText(record.getWithdrawalTokenAmount())) {
            record.setWithdrawalTokenAmount(toBaseUnits(record.getAmountUsdcReceived()));
        }
        if (!StringUtils.hasText(record.getRateId()) || !StringUtils.hasText(record.getRateSnapshotPda())
                || !StringUtils.hasText(record.getPaymentMint())) {
            throw new IllegalStateException("Thieu rate/mint da xac nhan tu Client payment");
        }
        payoutRecordRepository.save(record);
    }

    private void handleGatewayFailure(FreelancerPayoutRecord record, SolanaCprException exception) {
        boolean conflictMayBeAlreadySubmitted = exception.getHttpStatus() != null
                && exception.getHttpStatus() == 409;
        LocalDateTime since = record.getWithdrawalSubmittedAt() != null
                ? record.getWithdrawalSubmittedAt() : record.getClientPaymentConfirmedAt();
        if ((!conflictMayBeAlreadySubmitted && exception.isDefinitive()) || isExpired(since)) {
            fail(record, exception.getMessage());
        } else {
            record.setOnChainOffRampError(exception.getMessage());
            payoutRecordRepository.save(record);
            log.warn("On-chain off-ramp {} se retry: {}", record.getId(), exception.getMessage());
        }
    }

    private void fail(FreelancerPayoutRecord record, String message) {
        record.setOnChainOffRampStatus(OnChainOffRampStatus.FAILED);
        record.setOnChainOffRampError(message);
        payoutRecordRepository.save(record);
        log.error("Freelancer -> Treasury off-ramp {} that bai: {}", record.getId(), message);
    }

    private boolean isExpired(LocalDateTime since) {
        return since != null && since.plusSeconds(properties.getPendingExpirySeconds()).isBefore(LocalDateTime.now());
    }

    private String toBaseUnits(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("So luong USDC withdrawal khong hop le");
        }
        return amount.movePointRight(USDC_DECIMALS).setScale(0, RoundingMode.UNNECESSARY)
                .toBigIntegerExact().toString();
    }

    private boolean sameInteger(String left, String right) {
        try {
            return new BigInteger(left).equals(new BigInteger(right));
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
