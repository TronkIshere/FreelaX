package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.request.solana.CompleteOfframpRequest;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaOperationResult;
import com.marketplace.backend.dto.response.solana.SolanaTransactionStatusResult;
import com.marketplace.backend.dto.response.solana.SolanaWithdrawalResult;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.entity.OffRampStatus;
import com.marketplace.backend.entity.User;
import com.marketplace.backend.exception.SolanaCprException;
import com.marketplace.backend.provider.currency.OffRampProvider;
import com.marketplace.backend.provider.currency.OffRampResult;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.repository.UserRepository;
import com.marketplace.backend.service.VndPayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VndPayoutServiceImpl implements VndPayoutService {

    private static final String SEND_MODE = "send";
    private static final String PENDING = "Pending";
    private static final String COMPLETED = "Completed";
    private static final String FAILED_PENDING_REVIEW = "FailedPendingReview";

    private final OffRampProvider offRampProvider;
    private final UserRepository userRepository;
    private final SolanaCprClient solanaCprClient;
    private final SolanaCprProperties properties;
    private final FreelancerPayoutRecordRepository payoutRecordRepository;

    @Override
    @Transactional
    public void advance(FreelancerPayoutRecord record) {
        OffRampStatus status = record.getOffRampStatus() == null
                ? OffRampStatus.NOT_STARTED : record.getOffRampStatus();
        try {
            switch (status) {
                case NOT_STARTED -> simulateBankPayout(record);
                case SIMULATED -> submitOrConfirmCompletion(record);
                case COMPLETION_SUBMITTED -> confirmCompletion(record);
                default -> { }
            }
        } catch (SolanaCprException exception) {
            handleGatewayFailure(record, exception);
        } catch (RuntimeException exception) {
            fail(record, exception.getMessage());
        }
    }

    private void simulateBankPayout(FreelancerPayoutRecord record) {
        User freelancer = userRepository.findById(record.getFreelancerId())
                .orElseThrow(() -> new IllegalStateException("Khong tim thay Freelancer de payout VND"));
        if (freelancer.getBankCode() == null
                || !StringUtils.hasText(freelancer.getBankAccountNumber())
                || !StringUtils.hasText(freelancer.getBankAccountHolderName())) {
            throw new IllegalStateException("Freelancer chua co du thong tin tai khoan ngan hang");
        }

        OffRampResult result = offRampProvider.convertUsdcToVnd(record.getJobId(), record.getAmountUsdcReceived());
        record.setUsdcToVndRate(result.usdcToVndRate());
        record.setUsdcToVndRateSource(result.rateSource());
        record.setUsdcToVndRateObservedAt(result.rateObservedAt());
        record.setAmountVndBeforeOffRampFee(result.amountVndGross());
        record.setOffRampFeeVnd(result.feeVnd());
        record.setAmountVndEstimated(result.amountVndNet());
        record.setOffRampReference(result.payoutReference());
        record.setPayoutBankCode(freelancer.getBankCode());
        record.setPayoutBankAccountNumber(freelancer.getBankAccountNumber());
        record.setPayoutBankAccountHolderName(freelancer.getBankAccountHolderName());
        record.setSimulatedPayoutAt(LocalDateTime.now());
        record.setOffRampStatus(OffRampStatus.SIMULATED);
        record.setOffRampError(null);
        payoutRecordRepository.save(record);
    }

    private void submitOrConfirmCompletion(FreelancerPayoutRecord record) {
        Optional<SolanaWithdrawalResult> withdrawal = findWithdrawal(record);
        if (withdrawal.isPresent() && COMPLETED.equals(withdrawal.get().getStatus())) {
            applyCompleted(record, withdrawal.get());
            return;
        }
        if (withdrawal.isEmpty() || !PENDING.equals(withdrawal.get().getStatus())) {
            throw new IllegalStateException("WithdrawalRecord khong o trang thai Pending de chot payout");
        }

        SolanaConfigResult config = solanaCprClient.getConfig();
        if (!StringUtils.hasText(config.getOracleAuthority())) {
            throw new IllegalStateException("Config Solana khong co Oracle Authority");
        }
        SolanaOperationResult submitted = solanaCprClient.completeOfframp(
                record.getFreelancerPublicKey(), record.getWithdrawalId(), CompleteOfframpRequest.builder()
                        .oracleAuthority(config.getOracleAuthority())
                        .mode(SEND_MODE)
                        .commitment(properties.getCommitment())
                        .skipPreflight(false)
                        .build());
        record.setOffRampCompletionSignature(submitted.getSignature());
        record.setOffRampCompletionSubmittedAt(LocalDateTime.now());
        record.setOffRampStatus(OffRampStatus.COMPLETION_SUBMITTED);
        record.setOffRampError(null);
        payoutRecordRepository.save(record);
    }

    private void confirmCompletion(FreelancerPayoutRecord record) {
        Optional<SolanaWithdrawalResult> withdrawal = findWithdrawal(record);
        if (withdrawal.isPresent() && COMPLETED.equals(withdrawal.get().getStatus())) {
            applyCompleted(record, withdrawal.get());
            return;
        }
        if (withdrawal.isPresent() && FAILED_PENDING_REVIEW.equals(withdrawal.get().getStatus())) {
            fail(record, "Withdrawal dang FailedPendingReview");
            return;
        }
        if (StringUtils.hasText(record.getOffRampCompletionSignature())) {
            SolanaTransactionStatusResult transaction = solanaCprClient.getTransactionStatus(
                    record.getOffRampCompletionSignature());
            if (transaction.hasError()) {
                fail(record, "record_offramp loi on-chain: " + transaction.getError());
                return;
            }
        }
        if (isExpired(record.getOffRampCompletionSubmittedAt())) {
            fail(record, "WithdrawalRecord chua Completed sau " + properties.getPendingExpirySeconds() + " giay");
        } else {
            record.setOffRampError("Dang cho WithdrawalRecord chuyen sang Completed");
            payoutRecordRepository.save(record);
        }
    }

    private Optional<SolanaWithdrawalResult> findWithdrawal(FreelancerPayoutRecord record) {
        return solanaCprClient.findWithdrawal(record.getFreelancerPublicKey(), record.getWithdrawalId());
    }

    private void applyCompleted(FreelancerPayoutRecord record, SolanaWithdrawalResult withdrawal) {
        if (!sameInteger(withdrawal.getWithdrawalId(), record.getWithdrawalId())
                || !Objects.equals(withdrawal.getFreelancer(), record.getFreelancerPublicKey())
                || !sameInteger(withdrawal.getTokenAmount(), record.getWithdrawalTokenAmount())
                || !Objects.equals(withdrawal.getAddress(), record.getWithdrawalPda())
                || !COMPLETED.equals(withdrawal.getStatus())) {
            throw new IllegalStateException("WithdrawalRecord Completed khong khop payout record");
        }
        record.setOffRampStatus(OffRampStatus.COMPLETED);
        record.setOffRampCompletedAt(LocalDateTime.now());
        record.setOffRampError(null);
        payoutRecordRepository.save(record);
    }

    private void handleGatewayFailure(FreelancerPayoutRecord record, SolanaCprException exception) {
        if (exception.isDefinitive() || isExpired(record.getOffRampCompletionSubmittedAt())) {
            fail(record, exception.getMessage());
        } else {
            record.setOffRampError(exception.getMessage());
            payoutRecordRepository.save(record);
            log.warn("VND payout completion {} se retry: {}", record.getId(), exception.getMessage());
        }
    }

    private void fail(FreelancerPayoutRecord record, String message) {
        record.setOffRampStatus(OffRampStatus.FAILED);
        record.setOffRampError(message);
        payoutRecordRepository.save(record);
        log.error("VND payout mo phong {} that bai: {}", record.getId(), message);
    }

    private boolean isExpired(LocalDateTime since) {
        return since != null && since.plusSeconds(properties.getPendingExpirySeconds()).isBefore(LocalDateTime.now());
    }

    private boolean sameInteger(String left, String right) {
        try {
            return new BigInteger(left).equals(new BigInteger(right));
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
