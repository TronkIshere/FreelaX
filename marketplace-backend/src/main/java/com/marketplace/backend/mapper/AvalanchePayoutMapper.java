package com.marketplace.backend.mapper;

import com.marketplace.backend.dto.response.avalanche.AvalanchePayoutView;
import com.marketplace.backend.dto.response.avalanche.AvalanchePreparedTransfer;
import com.marketplace.backend.entity.AvalanchePayoutRequest;
import com.marketplace.backend.entity.AvalanchePayoutStatus;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Locale;

@Component
public class AvalanchePayoutMapper {

    public static final String NOTICE = "Demonstration only: USDC testnet hoặc mô phỏng, không có giá trị thực. Việc xác nhận chuyển trên chuỗi và việc đối tác xác nhận"
            + " chi trả bằng tiền pháp định là hai bước riêng; bước này không kéo theo bước kia.";
    /** Reported for payout rows created before V9, which carry no fee or tax figures. */
    public static final String TAX_TREATMENT = "NOT_DEFINED";

    private static final String ERC20_TRANSFER_SELECTOR = "0xa9059cbb";
    private static final int MAX_UINT_BITS = 256;

    public AvalanchePayoutView toView(AvalanchePayoutRequest p) {
        return new AvalanchePayoutView(p.getId(), p.getContractId(), p.getNetworkId(), p.getEnvironment(), p.getChainId(), p.getTokenAddress(),
                p.getAmountBaseUnits(), p.getFromAddress(), p.getDepositAddress(), p.getPartnerId(), p.isPartnerSimulated(),
                p.getPartnerReportedFee(), p.getPartnerReportedRate(), p.getStatus(), p.getTransferTxHash(),
                p.getTransferChainState(), p.getTransferMatch(), p.getStatus().onChainTransferConfirmed(), p.getTransferConfirmedAt(),
                p.getStatus() == AvalanchePayoutStatus.FIAT_PAYOUT_CONFIRMED, p.getFiatReference(), p.getFiatConfirmedAt(),
                p.getFailureReason(), p.getTaxTreatment() == null ? TAX_TREATMENT : p.getTaxTreatment(), NOTICE, toPreparedTransfer(p),
                p.getPlatformFeeUsd(), p.getTaxUsdToVndRate(), p.getTaxRateSource(), p.getTaxableAmountVnd());
    }

    /**
     * The exact ERC-20 {@code transfer(deposit, amount)} the freelancer's own wallet must send, built by the backend (the UI
     * never builds calldata). Present only while a transfer is awaited. FreelaX never signs or sends it.
     */
    private AvalanchePreparedTransfer toPreparedTransfer(AvalanchePayoutRequest p) {
        if (p.getStatus() != AvalanchePayoutStatus.AWAITING_USER_TRANSFER && p.getStatus() != AvalanchePayoutStatus.TRANSFER_FAILED) {
            return null;
        }
        return new AvalanchePreparedTransfer(p.getChainId(), p.getTokenAddress(), erc20Transfer(p.getDepositAddress(), p.getAmountBaseUnits()), "0");
    }

    private static String erc20Transfer(String to, String amountBaseUnits) {
        BigInteger amount = new BigInteger(amountBaseUnits);
        if (amount.signum() <= 0 || amount.bitLength() > MAX_UINT_BITS) {
            throw new IllegalArgumentException("amount out of range");
        }
        String address = to.substring(2).toLowerCase(Locale.ROOT);
        return ERC20_TRANSFER_SELECTOR + "0".repeat(24) + address + String.format("%64s", amount.toString(16)).replace(' ', '0');
    }
}
