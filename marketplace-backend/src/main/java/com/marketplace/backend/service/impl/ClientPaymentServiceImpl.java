package com.marketplace.backend.service.impl;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.request.solana.CreateInvoiceRequest;
import com.marketplace.backend.dto.request.solana.PayInvoiceRequest;
import com.marketplace.backend.dto.request.solana.PublishRateRequest;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaInvoiceResult;
import com.marketplace.backend.dto.response.solana.SolanaOperationResult;
import com.marketplace.backend.dto.response.solana.SolanaRateResult;
import com.marketplace.backend.dto.response.solana.SolanaTransactionStatusResult;
import com.marketplace.backend.entity.ClientPaymentStatus;
import com.marketplace.backend.entity.FreelancerPayoutRecord;
import com.marketplace.backend.exception.SolanaCprException;
import com.marketplace.backend.repository.FreelancerPayoutRecordRepository;
import com.marketplace.backend.service.ClientPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientPaymentServiceImpl implements ClientPaymentService {

    private static final int USDC_DECIMALS = 6;
    private static final String SEND_MODE = "send";
    private static final String PAID = "Paid";

    private final SolanaCprClient solanaCprClient;
    private final SolanaCprProperties properties;
    private final FreelancerPayoutRecordRepository payoutRecordRepository;

    @Override
    @Transactional
    public void advance(FreelancerPayoutRecord record) {
        ClientPaymentStatus currentStatus = record.getClientPaymentStatus() == null
                ? ClientPaymentStatus.NOT_STARTED : record.getClientPaymentStatus();
        if (currentStatus == ClientPaymentStatus.CONFIRMED
                || currentStatus == ClientPaymentStatus.FAILED) {
            return;
        }
        if (record.getClientPaymentStatus() == null) {
            record.setClientPaymentStatus(currentStatus);
            payoutRecordRepository.save(record);
        }
        try {
            switch (currentStatus) {
                case NOT_STARTED -> publishOrConfirmRate(record);
                case RATE_SUBMITTED -> confirmRate(record);
                case RATE_CONFIRMED -> createOrConfirmInvoice(record);
                case INVOICE_SUBMITTED -> confirmInvoice(record);
                case INVOICE_CREATED -> payOrConfirmInvoice(record);
                case PAYMENT_SUBMITTED -> confirmPayment(record);
                default -> { }
            }
        } catch (SolanaCprException exception) {
            handleGatewayFailure(record, exception);
        } catch (RuntimeException exception) {
            fail(record, exception.getMessage());
        }
    }

    private void publishOrConfirmRate(FreelancerPayoutRecord record) {
        initializeIdentifiers(record);
        Optional<SolanaRateResult> existing = solanaCprClient.findRate(record.getRateId());
        if (existing.isPresent()) {
            applyConfirmedRate(record, existing.get());
            return;
        }

        SolanaConfigResult config = solanaCprClient.getConfig();
        if (!StringUtils.hasText(config.getRateAuthority())) {
            throw new IllegalStateException("Config Solana khong co rateAuthority");
        }

        long now = Instant.now().getEpochSecond();
        long expiresAt = now + properties.getInvoiceValiditySeconds();
        record.setInvoiceExpiresAtEpoch(expiresAt);

        String usdVndE6 = record.getTaxUsdToVndRate().movePointRight(USDC_DECIMALS)
                .setScale(0, RoundingMode.HALF_UP).toBigIntegerExact().toString();
        String source = record.getJobId() + ":" + record.getRateId() + ":" + usdVndE6 + ":" + now;
        SolanaOperationResult submitted = solanaCprClient.publishRate(PublishRateRequest.builder()
                .rateAuthority(config.getRateAuthority())
                .rateId(record.getRateId())
                .usdcUsdE6("1000000")
                .usdVndE6(usdVndE6)
                .observedAt(Long.toString(now))
                .expiresAt(Long.toString(expiresAt))
                .sourceHash(sha256(source))
                .mode(SEND_MODE)
                .commitment(properties.getCommitment())
                .skipPreflight(false)
                .build());

        record.setRateTransactionSignature(submitted.getSignature());
        if (submitted.getDerivedAccounts() != null) {
            record.setRateSnapshotPda(submitted.getDerivedAccounts().getRateSnapshot());
        }
        record.setClientPaymentStatus(ClientPaymentStatus.RATE_SUBMITTED);
        markSubmitted(record);
    }

    private void confirmRate(FreelancerPayoutRecord record) {
        Optional<SolanaRateResult> rate = solanaCprClient.findRate(record.getRateId());
        if (rate.isPresent()) {
            applyConfirmedRate(record, rate.get());
            return;
        }
        waitOrFail(record, record.getRateTransactionSignature(), "RateSnapshot chua duoc xac nhan");
    }

    private void applyConfirmedRate(FreelancerPayoutRecord record, SolanaRateResult rate) {
        String expectedUsdVndE6 = record.getTaxUsdToVndRate().movePointRight(USDC_DECIMALS)
                .setScale(0, RoundingMode.HALF_UP).toBigIntegerExact().toString();
        if (!sameInteger(rate.getRateId(), record.getRateId())
                || !sameInteger(rate.getUsdcUsdE6(), "1000000")
                || !sameInteger(rate.getUsdVndE6(), expectedUsdVndE6)) {
            throw new IllegalStateException("RateSnapshot on-chain khong khop payout record");
        }
        long expiresAt = Long.parseLong(rate.getExpiresAt());
        if (expiresAt <= Instant.now().getEpochSecond()) {
            throw new IllegalStateException("RateSnapshot on-chain da het han");
        }
        record.setRateSnapshotPda(rate.getAddress());
        if (record.getInvoiceExpiresAtEpoch() == null) {
            record.setInvoiceExpiresAtEpoch(expiresAt);
        }
        record.setClientPaymentStatus(ClientPaymentStatus.RATE_CONFIRMED);
        record.setClientPaymentError(null);
        payoutRecordRepository.save(record);
    }

    private void createOrConfirmInvoice(FreelancerPayoutRecord record) {
        Optional<SolanaInvoiceResult> existing = solanaCprClient.findInvoice(
                record.getFreelancerPublicKey(), record.getInvoiceId());
        if (existing.isPresent()) {
            applyInvoice(record, existing.get());
            return;
        }
        if (record.getInvoiceExpiresAtEpoch() == null
                || record.getInvoiceExpiresAtEpoch() <= Instant.now().getEpochSecond()) {
            fail(record, "RateSnapshot/Invoice da het han truoc khi tao Invoice");
            return;
        }

        SolanaOperationResult submitted = solanaCprClient.createInvoice(CreateInvoiceRequest.builder()
                .freelancer(record.getFreelancerPublicKey())
                .invoiceId(record.getInvoiceId())
                .client(record.getOnRampClientPublicKey())
                .amount(paymentAmountBaseUnits(record))
                .rateId(record.getRateId())
                .expiresAt(Long.toString(record.getInvoiceExpiresAtEpoch()))
                .mode(SEND_MODE)
                .commitment(properties.getCommitment())
                .skipPreflight(false)
                .build());
        record.setInvoiceTransactionSignature(submitted.getSignature());
        if (submitted.getDerivedAccounts() != null) {
            record.setInvoicePda(submitted.getDerivedAccounts().getInvoice());
        }
        record.setClientPaymentStatus(ClientPaymentStatus.INVOICE_SUBMITTED);
        markSubmitted(record);
    }

    private void confirmInvoice(FreelancerPayoutRecord record) {
        Optional<SolanaInvoiceResult> invoice = solanaCprClient.findInvoice(
                record.getFreelancerPublicKey(), record.getInvoiceId());
        if (invoice.isPresent()) {
            applyInvoice(record, invoice.get());
            return;
        }
        waitOrFail(record, record.getInvoiceTransactionSignature(), "Invoice chua duoc xac nhan");
    }

    private void applyInvoice(FreelancerPayoutRecord record, SolanaInvoiceResult invoice) {
        verifyInvoice(record, invoice);
        record.setInvoicePda(invoice.getAddress());
        record.setPaymentMint(invoice.getMint());
        if (PAID.equals(invoice.getStatus())) {
            markPaymentConfirmed(record);
        } else if ("Pending".equals(invoice.getStatus())) {
            record.setClientPaymentStatus(ClientPaymentStatus.INVOICE_CREATED);
            record.setClientPaymentError(null);
            payoutRecordRepository.save(record);
        } else {
            fail(record, "Invoice co trang thai khong the thanh toan: " + invoice.getStatus());
        }
    }

    private void payOrConfirmInvoice(FreelancerPayoutRecord record) {
        Optional<SolanaInvoiceResult> existing = solanaCprClient.findInvoice(
                record.getFreelancerPublicKey(), record.getInvoiceId());
        if (existing.isPresent() && PAID.equals(existing.get().getStatus())) {
            applyInvoice(record, existing.get());
            return;
        }
        if (existing.isEmpty()) {
            throw new IllegalStateException("Invoice bien mat truoc khi thanh toan");
        }
        verifyInvoice(record, existing.get());

        SolanaOperationResult submitted = solanaCprClient.payInvoice(
                record.getFreelancerPublicKey(), record.getInvoiceId(), PayInvoiceRequest.builder()
                        .client(record.getOnRampClientPublicKey())
                        .mode(SEND_MODE)
                        .commitment(properties.getCommitment())
                        .skipPreflight(false)
                        .build());
        record.setPaymentTransactionSignature(submitted.getSignature());
        record.setClientPaymentStatus(ClientPaymentStatus.PAYMENT_SUBMITTED);
        markSubmitted(record);
    }

    private void confirmPayment(FreelancerPayoutRecord record) {
        Optional<SolanaInvoiceResult> invoice = solanaCprClient.findInvoice(
                record.getFreelancerPublicKey(), record.getInvoiceId());
        if (invoice.isPresent() && PAID.equals(invoice.get().getStatus())) {
            applyInvoice(record, invoice.get());
            return;
        }
        waitOrFail(record, record.getPaymentTransactionSignature(), "Invoice chua chuyen sang Paid");
    }

    private void verifyInvoice(FreelancerPayoutRecord record, SolanaInvoiceResult invoice) {
        if (!sameInteger(invoice.getInvoiceId(), record.getInvoiceId())
                || !Objects.equals(invoice.getClient(), record.getOnRampClientPublicKey())
                || !Objects.equals(invoice.getFreelancer(), record.getFreelancerPublicKey())
                || !sameInteger(invoice.getAmount(), paymentAmountBaseUnits(record))
                || (StringUtils.hasText(record.getRateSnapshotPda())
                && !Objects.equals(invoice.getRateSnapshot(), record.getRateSnapshotPda()))) {
            throw new IllegalStateException("Invoice on-chain khong khop payout record");
        }
    }

    private void waitOrFail(FreelancerPayoutRecord record, String signature, String pendingMessage) {
        if (StringUtils.hasText(signature)) {
            SolanaTransactionStatusResult transaction = solanaCprClient.getTransactionStatus(signature);
            if (transaction.hasError()) {
                fail(record, pendingMessage + ": " + transaction.getError());
                return;
            }
        }
        if (isExpired(record.getClientPaymentSubmittedAt())) {
            fail(record, pendingMessage + " sau " + properties.getPendingExpirySeconds() + " giay");
        } else {
            record.setClientPaymentError(pendingMessage);
            payoutRecordRepository.save(record);
        }
    }

    private void initializeIdentifiers(FreelancerPayoutRecord record) {
        if (!StringUtils.hasText(record.getRateId())) {
            record.setRateId(Long.toUnsignedString(record.getJobId().getLeastSignificantBits()));
        }
        if (!StringUtils.hasText(record.getInvoiceId())) {
            record.setInvoiceId(Long.toUnsignedString(record.getJobId().getMostSignificantBits()));
        }
        payoutRecordRepository.save(record);
    }

    private void markSubmitted(FreelancerPayoutRecord record) {
        record.setClientPaymentSubmittedAt(LocalDateTime.now());
        record.setClientPaymentError(null);
        payoutRecordRepository.save(record);
    }

    private void markPaymentConfirmed(FreelancerPayoutRecord record) {
        record.setClientPaymentStatus(ClientPaymentStatus.CONFIRMED);
        record.setClientPaymentConfirmedAt(LocalDateTime.now());
        record.setClientPaymentError(null);
        payoutRecordRepository.save(record);
    }

    private void handleGatewayFailure(FreelancerPayoutRecord record, SolanaCprException exception) {
        boolean conflictMayBeAnAlreadySubmittedTransaction = exception.getHttpStatus() != null
                && exception.getHttpStatus() == 409;
        LocalDateTime since = record.getClientPaymentSubmittedAt() != null
                ? record.getClientPaymentSubmittedAt() : record.getCreatedAt();
        if ((!conflictMayBeAnAlreadySubmittedTransaction && exception.isDefinitive()) || isExpired(since)) {
            fail(record, exception.getMessage());
        } else {
            record.setClientPaymentError(exception.getMessage());
            payoutRecordRepository.save(record);
            log.warn("Client payment {} se retry: {}", record.getId(), exception.getMessage());
        }
    }

    private void fail(FreelancerPayoutRecord record, String message) {
        record.setClientPaymentStatus(ClientPaymentStatus.FAILED);
        record.setClientPaymentError(message);
        payoutRecordRepository.save(record);
        log.error("Client -> Freelancer payment {} that bai: {}", record.getId(), message);
    }

    private boolean isExpired(LocalDateTime since) {
        return since != null && since.plusSeconds(properties.getPendingExpirySeconds()).isBefore(LocalDateTime.now());
    }

    private String paymentAmountBaseUnits(FreelancerPayoutRecord record) {
        BigDecimal amount = record.getAmountUsdcReceived();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("So luong USDC thanh toan khong hop le");
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

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
