package com.marketplace.backend.provider.onchain;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.configuration.SolanaCprProperties;
import com.marketplace.backend.dto.request.solana.MockOnrampPurchaseRequest;
import com.marketplace.backend.dto.response.solana.DerivedAccountsResult;
import com.marketplace.backend.dto.response.solana.MockOnrampPurchaseResult;
import com.marketplace.backend.dto.response.solana.MockOnrampReceiptResult;
import com.marketplace.backend.dto.response.solana.SolanaConfigResult;
import com.marketplace.backend.dto.response.solana.SolanaTransactionStatusResult;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.exception.SolanaCprException;
import com.marketplace.backend.provider.currency.OnRampProvider;
import com.marketplace.backend.provider.currency.OnRampQuote;
import com.marketplace.backend.provider.currency.OnRampResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class SolanaOnRampProvider implements OnRampProvider {

    private static final int USDC_DECIMALS = 6;
    private static final String MODE_SEND = "send";
    private static final Set<String> CONFIRMED_STATUSES = Set.of("confirmed", "finalized");

    private final SolanaCprClient solanaCprClient;
    private final SolanaCprProperties properties;

    @Value("${onramp.fee-rate:0.005}")
    private BigDecimal feeRate;

    public SolanaOnRampProvider(SolanaCprClient solanaCprClient, SolanaCprProperties properties) {
        this.solanaCprClient = solanaCprClient;
        this.properties = properties;
    }

    @Override
    public String network() {
        return properties.getNetwork();
    }

    @Override
    public OnRampQuote quote(UUID reference, BigDecimal amountUsd) {
        BigDecimal amountUsdSource = amountUsd.setScale(USDC_DECIMALS, RoundingMode.HALF_UP);
        BigDecimal feeUsd = amountUsdSource.multiply(feeRate).setScale(USDC_DECIMALS, RoundingMode.HALF_UP);
        BigDecimal amountUsdNet = amountUsdSource.subtract(feeUsd);
        String usdAmountE6 = amountUsdNet.movePointRight(USDC_DECIMALS)
                .setScale(0, RoundingMode.DOWN)
                .toBigIntegerExact()
                .toString();
        String purchaseId = Long.toUnsignedString(reference.getMostSignificantBits());
        return new OnRampQuote(amountUsdSource, feeUsd, amountUsdNet, usdAmountE6, purchaseId);
    }

    @Override
    public OnRampResult execute(OnRampQuote quote, String recipientPublicKey) {
        if (!StringUtils.hasText(properties.getOnrampAuthorityPublicKey())) {
            throw new ApplicationException(ErrorCode.SOLANA_CPR_NOT_CONFIGURED, "solana-cpr.onramp-authority-public-key");
        }

        SolanaConfigResult config = readConfigOrNull();
        String precheckError = precheckConfig(config);
        if (precheckError != null) {
            return OnRampResult.failed(null, null, null, precheckError);
        }

        Optional<MockOnrampReceiptResult> existing;
        try {
            existing = solanaCprClient.findMockOnrampReceipt(recipientPublicKey, quote.purchaseId());
        } catch (SolanaCprException e) {
            return OnRampResult.notStarted("Khong doc duoc receipt truoc khi submit, chua gui transaction: " + e.getMessage());
        }

        if (existing.isPresent()) {
            return fromReceipt(existing.get(), quote, recipientPublicKey, null, null, null, config);
        }

        MockOnrampPurchaseRequest request = MockOnrampPurchaseRequest.builder()
                .onrampAuthority(properties.getOnrampAuthorityPublicKey())
                .client(recipientPublicKey)
                .purchaseId(quote.purchaseId())
                .usdAmountE6(quote.usdAmountE6())
                .mode(MODE_SEND)
                .commitment(properties.getCommitment())
                .skipPreflight(false)
                .build();

        MockOnrampPurchaseResult submitted;
        try {
            submitted = solanaCprClient.createMockOnrampPurchase(request);
        } catch (SolanaCprException e) {
            if (e.isDefinitive()) {
                return OnRampResult.failed(null, null, null, "mock_onramp bi tu choi: " + e.getMessage());
            }
            return OnRampResult.submitted(null, null, null,
                    "Khong chac transaction da duoc gui, se doi chieu lai qua receipt: " + e.getMessage());
        }

        DerivedAccountsResult accounts = submitted.getDerivedAccounts();
        String clientAta = accounts != null ? accounts.getClientAta() : null;
        String receiptPda = accounts != null ? accounts.getMockOnrampReceipt() : null;

        return awaitConfirmation(quote, recipientPublicKey, submitted.getSignature(), clientAta, receiptPda, config);
    }

    @Override
    public OnRampResult resume(OnRampQuote quote, String recipientPublicKey, String signature,
                               String clientUsdcAta, String receiptPda, LocalDateTime submittedAt) {
        SolanaConfigResult config = readConfigOrNull();

        Optional<MockOnrampReceiptResult> receipt;
        try {
            receipt = solanaCprClient.findMockOnrampReceipt(recipientPublicKey, quote.purchaseId());
        } catch (SolanaCprException e) {
            return OnRampResult.submitted(signature, clientUsdcAta, receiptPda, "Khong doc duoc receipt: " + e.getMessage());
        }

        if (receipt.isPresent()) {
            return fromReceipt(receipt.get(), quote, recipientPublicKey, signature, clientUsdcAta, receiptPda, config);
        }

        if (StringUtils.hasText(signature)) {
            try {
                SolanaTransactionStatusResult status = solanaCprClient.getTransactionStatus(signature);
                if (status.hasError()) {
                    return OnRampResult.failed(signature, clientUsdcAta, receiptPda,
                            "Transaction loi on-chain: " + status.getError());
                }
            } catch (SolanaCprException e) {
                return OnRampResult.submitted(signature, clientUsdcAta, receiptPda,
                        "Khong doc duoc trang thai transaction: " + e.getMessage());
            }
        }

        if (submittedAt != null && submittedAt.plusSeconds(properties.getPendingExpirySeconds()).isBefore(LocalDateTime.now())) {
            return OnRampResult.failed(signature, clientUsdcAta, receiptPda,
                    "Qua " + properties.getPendingExpirySeconds() + " giay khong tim thay receipt, coi nhu transaction khong duoc ghi nhan");
        }

        return OnRampResult.submitted(signature, clientUsdcAta, receiptPda, "Dang cho xac nhan");
    }

    private OnRampResult awaitConfirmation(OnRampQuote quote, String recipientPublicKey, String signature,
                                           String clientAta, String receiptPda, SolanaConfigResult config) {
        int attempts = Math.max(1, properties.getConfirmPollAttempts());
        long deadline = System.currentTimeMillis() + attempts * properties.getConfirmPollIntervalMs();
        String lastState = "chua kiem tra";

        for (int attempt = 1; attempt <= attempts && System.currentTimeMillis() <= deadline; attempt++) {
            try {
                SolanaTransactionStatusResult status = solanaCprClient.getTransactionStatus(signature);
                if (status.hasError()) {
                    return OnRampResult.failed(signature, clientAta, receiptPda, "Transaction loi on-chain: " + status.getError());
                }
                lastState = "found=" + status.isFound() + ", confirmationStatus=" + status.getConfirmationStatus();
                if (status.isFound() && isConfirmed(status.getConfirmationStatus())) {
                    return confirmFromReceipt(quote, recipientPublicKey, signature, clientAta, receiptPda, config);
                }
            } catch (SolanaCprException e) {
                lastState = e.getMessage();
            }

            if (attempt < attempts && !sleep(properties.getConfirmPollIntervalMs())) {
                break;
            }
        }

        return OnRampResult.submitted(signature, clientAta, receiptPda,
                "Chua xac nhan sau " + attempts + " lan kiem tra (" + lastState + ")");
    }

    private OnRampResult confirmFromReceipt(OnRampQuote quote, String recipientPublicKey, String signature,
                                            String clientAta, String receiptPda, SolanaConfigResult config) {
        try {
            return solanaCprClient.findMockOnrampReceipt(recipientPublicKey, quote.purchaseId())
                    .map(receipt -> fromReceipt(receipt, quote, recipientPublicKey, signature, clientAta, receiptPda, config))
                    .orElseGet(() -> OnRampResult.submitted(signature, clientAta, receiptPda,
                            "Transaction da confirmed nhung chua doc duoc receipt"));
        } catch (SolanaCprException e) {
            return OnRampResult.submitted(signature, clientAta, receiptPda, "Khong doc duoc receipt: " + e.getMessage());
        }
    }

    private OnRampResult fromReceipt(MockOnrampReceiptResult receipt, OnRampQuote quote, String recipientPublicKey,
                                     String signature, String expectedClientAta, String expectedReceiptPda,
                                     SolanaConfigResult config) {
        String clientAta = StringUtils.hasText(receipt.getClientAta()) ? receipt.getClientAta() : expectedClientAta;
        String receiptPda = StringUtils.hasText(receipt.getAddress()) ? receipt.getAddress() : expectedReceiptPda;

        String conflict = verifyReceipt(receipt, quote, recipientPublicKey, expectedClientAta, expectedReceiptPda, config);
        if (conflict != null) {
            return OnRampResult.failed(signature, clientAta, receiptPda, "Receipt khong khop, dung xu ly: " + conflict);
        }

        BigDecimal amountUsdc = new BigDecimal(new BigInteger(receipt.getTokenAmount()), USDC_DECIMALS);
        return OnRampResult.confirmed(signature, clientAta, receiptPda, amountUsdc);
    }

    private String verifyReceipt(MockOnrampReceiptResult receipt, OnRampQuote quote, String recipientPublicKey,
                                 String expectedClientAta, String expectedReceiptPda, SolanaConfigResult config) {
        if (!sameInteger(receipt.getPurchaseId(), quote.purchaseId())) {
            return "purchaseId=" + receipt.getPurchaseId() + " (mong doi " + quote.purchaseId() + ")";
        }
        if (!Objects.equals(receipt.getClient(), recipientPublicKey)) {
            return "client=" + receipt.getClient() + " (mong doi " + recipientPublicKey + ")";
        }
        if (StringUtils.hasText(expectedClientAta) && StringUtils.hasText(receipt.getClientAta())
                && !expectedClientAta.equals(receipt.getClientAta())) {
            return "clientAta=" + receipt.getClientAta() + " (mong doi " + expectedClientAta + ")";
        }
        if (StringUtils.hasText(expectedReceiptPda) && StringUtils.hasText(receipt.getAddress())
                && !expectedReceiptPda.equals(receipt.getAddress())) {
            return "receiptPda=" + receipt.getAddress() + " (mong doi " + expectedReceiptPda + ")";
        }
        if (config != null && StringUtils.hasText(config.getAcceptedMint()) && StringUtils.hasText(receipt.getMint())
                && !config.getAcceptedMint().equals(receipt.getMint())) {
            return "mint=" + receipt.getMint() + " (mong doi " + config.getAcceptedMint() + ")";
        }
        if (!sameInteger(receipt.getUsdAmountE6(), quote.usdAmountE6())) {
            return "usdAmountE6=" + receipt.getUsdAmountE6() + " (mong doi " + quote.usdAmountE6() + ")";
        }
        if (!sameInteger(receipt.getTokenAmount(), quote.usdAmountE6())) {
            return "tokenAmount=" + receipt.getTokenAmount() + " (mong doi " + quote.usdAmountE6() + ")";
        }
        return null;
    }

    private String precheckConfig(SolanaConfigResult config) {
        if (config == null) {
            return null;
        }
        if (Boolean.TRUE.equals(config.getPaused())) {
            return "Config Solana dang paused";
        }
        if (Boolean.FALSE.equals(config.getMockOnrampEnabled())) {
            return "Mock on-ramp dang bi tat tren chain (mockOnrampEnabled=false)";
        }
        if (StringUtils.hasText(config.getMockOnrampAuthority())
                && !config.getMockOnrampAuthority().equals(properties.getOnrampAuthorityPublicKey())) {
            return "onrampAuthority cau hinh (" + properties.getOnrampAuthorityPublicKey()
                    + ") khac Config.mockOnrampAuthority (" + config.getMockOnrampAuthority() + ")";
        }
        return null;
    }

    private SolanaConfigResult readConfigOrNull() {
        try {
            return solanaCprClient.getConfig();
        } catch (SolanaCprException e) {
            log.warn("Khong doc duoc Config Solana, bo qua precheck: {}", e.getMessage());
            return null;
        }
    }

    private boolean isConfirmed(String confirmationStatus) {
        return confirmationStatus != null && CONFIRMED_STATUSES.contains(confirmationStatus.trim().toLowerCase());
    }

    private boolean sameInteger(String actual, String expected) {
        if (!StringUtils.hasText(actual) || !StringUtils.hasText(expected)) {
            return false;
        }
        try {
            return new BigInteger(actual.trim()).equals(new BigInteger(expected.trim()));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}