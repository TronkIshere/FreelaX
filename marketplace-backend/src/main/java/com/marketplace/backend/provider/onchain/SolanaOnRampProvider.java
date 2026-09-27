package com.marketplace.backend.provider.onchain;

import com.marketplace.backend.client.SolanaCprClient;
import com.marketplace.backend.dto.request.solana.MockOnrampPurchaseRequest;
import com.marketplace.backend.dto.response.solana.MockOnrampPurchaseResult;
import com.marketplace.backend.dto.response.solana.SolanaTransactionStatusResult;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.provider.currency.OnRampProvider;
import com.marketplace.backend.provider.currency.OnRampResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Set;
import java.util.UUID;

@Component
public class SolanaOnRampProvider implements OnRampProvider {

    private static final int USDC_DECIMALS = 6;
    private static final Set<String> CONFIRMED_STATUSES = Set.of("CONFIRMED", "FINALIZED");

    private final SolanaCprClient solanaCprClient;

    @Value("${solana-cpr.custodial-client-public-key:}")
    private String custodialClientPublicKey;

    @Value("${onramp.fee-rate:0.005}")
    private BigDecimal feeRate;

    public SolanaOnRampProvider(SolanaCprClient solanaCprClient) {
        this.solanaCprClient = solanaCprClient;
    }

    @Override
    public OnRampResult convertUsdToUsdc(UUID reference, BigDecimal amountUsd) {
        if (!StringUtils.hasText(custodialClientPublicKey)) {
            throw new ApplicationException(ErrorCode.SOLANA_CPR_NOT_CONFIGURED, "solana-cpr.custodial-client-public-key");
        }

        BigDecimal amountUsdSource = amountUsd.setScale(USDC_DECIMALS, RoundingMode.HALF_UP);
        BigDecimal feeUsd = amountUsdSource.multiply(feeRate).setScale(USDC_DECIMALS, RoundingMode.HALF_UP);
        BigDecimal amountUsdNet = amountUsdSource.subtract(feeUsd);

        String purchaseId = toPurchaseId(reference);
        MockOnrampPurchaseRequest request = MockOnrampPurchaseRequest.builder()
                .clientPublicKey(custodialClientPublicKey)
                .purchaseId(purchaseId)
                .usdAmountE6(toBaseUnits(amountUsdNet).toString())
                .idempotencyKey("onramp-job-" + reference)
                .build();

        MockOnrampPurchaseResult purchase = solanaCprClient.createMockOnrampPurchase(request);
        ensureConfirmed(purchase);

        BigDecimal amountUsdcReceived = StringUtils.hasText(purchase.getTokenAmountBaseUnits())
                ? fromBaseUnits(new BigInteger(purchase.getTokenAmountBaseUnits()))
                : amountUsdNet;

        return new OnRampResult(
                amountUsdSource,
                feeUsd,
                amountUsdNet,
                amountUsdcReceived,
                purchaseId,
                purchase.getSignature(),
                purchase.getClientUsdcAta(),
                purchase.getReceiptPda()
        );
    }

    private void ensureConfirmed(MockOnrampPurchaseResult purchase) {
        if (CONFIRMED_STATUSES.contains(normalize(purchase.getStatus()))) {
            return;
        }

        SolanaTransactionStatusResult tx = solanaCprClient.getTransactionStatus(purchase.getSignature());
        if (!CONFIRMED_STATUSES.contains(normalize(tx.getStatus())) || StringUtils.hasText(tx.getError())) {
            throw new ApplicationException(ErrorCode.SOLANA_ONRAMP_NOT_CONFIRMED,
                    purchase.getSignature() + " status=" + tx.getStatus() + " error=" + tx.getError());
        }
    }

    private String normalize(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private String toPurchaseId(UUID reference) {
        return Long.toUnsignedString(reference.getMostSignificantBits() & Long.MAX_VALUE);
    }

    private BigInteger toBaseUnits(BigDecimal amount) {
        return amount.movePointRight(USDC_DECIMALS).setScale(0, RoundingMode.DOWN).toBigIntegerExact();
    }

    private BigDecimal fromBaseUnits(BigInteger baseUnits) {
        return new BigDecimal(baseUnits, USDC_DECIMALS);
    }
}