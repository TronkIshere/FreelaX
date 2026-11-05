package com.marketplace.backend.payout.avalanche;

import com.marketplace.backend.blockchain.avalanche.AvalancheEscrowIds;
import com.marketplace.backend.client.AvalancheClient;
import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.dto.response.avalanche.AvalancheResults;
import com.marketplace.backend.entity.EvmWallet;
import com.marketplace.backend.entity.Milestone;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.EvmWalletRepository;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Independent, non-custodial Avalanche payout adapter (Phase 3, delegated engineering; real partner integration disabled).
 * After an Avalanche escrow released to the freelancer, the USDC sits in the freelancer's OWN wallet. A payout is: the
 * partner (if it explicitly supports this chain and token) issues a deposit address; the freelancer's own wallet sends the
 * released amount there; the gateway proves the transfer from the receipt; then the partner reports the fiat payout.
 * FreelaX never holds funds, never signs for the user and never routes to another chain. Fee and tax (owner decision
 * 2026-11-05): the existing Solana-rail rules are applied unchanged as SIMULATED accounting figures ({@link SolanaRailFeeTaxRules});
 * they never change the amount the freelancer's wallet transfers, and no withheld tax is computed (MISA reports it on the Solana
 * rail; no MISA record is created for this rail).
 * The Solana settlement pipeline ({@code SettlementDownstreamService}) is not used or touched.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class AvalanchePayoutService {

    static final String NOTICE = "Demonstration only: Testnet/simulated USDC with no real-world value. The on-chain transfer"
            + " confirmation and the partner's fiat payout confirmation are separate; neither implies the other.";
    /** Reported for payout rows created before V9, which carry no fee or tax figures. */
    static final String TAX_TREATMENT = "NOT_DEFINED";
    private static final Pattern TX_HASH = Pattern.compile("0x[0-9a-fA-F]{64}");
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final Set<String> DEFINITIVE_MISMATCH = Set.of("TRANSFER_NOT_FOUND", "AMOUNT_MISMATCH", "AMBIGUOUS");

    private final WorkContractRepository contracts;
    private final MilestoneRepository milestones;
    private final EvmWalletRepository evmWallets;
    private final AvalanchePayoutRequestRepository payouts;
    private final AvalancheClient gateway;
    private final AvalancheGatewayProperties gatewayProperties;
    private final AvalanchePayoutProperties properties;
    private final AvalanchePayoutPartner partner;
    /** Same provider the Solana rail uses for its tax rate (PayoutServiceImpl.createRecord). */
    private final com.marketplace.backend.provider.currency.ExchangeRateProvider exchangeRates;

    public enum Outcome { CREATED, EXISTING, UNSUPPORTED_RAIL }

    public record PayoutView(UUID id, UUID contractId, String networkId, String environment, long chainId, String tokenAddress,
                             String amountBaseUnits, String fromAddress, String depositAddress, String partnerId, boolean partnerSimulated,
                             String partnerReportedFee, String partnerReportedRate, AvalanchePayoutStatus status,
                             String transferTxHash, String transferChainState, String transferMatch,
                             boolean onChainTransferConfirmed, Instant transferConfirmedAt,
                             boolean fiatPayoutConfirmed, String fiatReference, Instant fiatConfirmedAt,
                             String failureReason, String taxTreatment, String notice, PreparedTransfer preparedTransfer,
                             java.math.BigDecimal platformFeeUsd, java.math.BigDecimal taxUsdToVndRate,
                             com.marketplace.backend.entity.ExchangeRateSource taxRateSource, java.math.BigDecimal taxableAmountVnd) {
        static PayoutView of(AvalanchePayoutRequest p) {
            return new PayoutView(p.getId(), p.getContractId(), p.getNetworkId(), p.getEnvironment(), p.getChainId(), p.getTokenAddress(),
                    p.getAmountBaseUnits(), p.getFromAddress(), p.getDepositAddress(), p.getPartnerId(), p.isPartnerSimulated(),
                    p.getPartnerReportedFee(), p.getPartnerReportedRate(), p.getStatus(), p.getTransferTxHash(),
                    p.getTransferChainState(), p.getTransferMatch(), p.getStatus().onChainTransferConfirmed(), p.getTransferConfirmedAt(),
                    p.getStatus() == AvalanchePayoutStatus.FIAT_PAYOUT_CONFIRMED, p.getFiatReference(), p.getFiatConfirmedAt(),
                    p.getFailureReason(), p.getTaxTreatment() == null ? TAX_TREATMENT : p.getTaxTreatment(), NOTICE, PreparedTransfer.of(p),
                    p.getPlatformFeeUsd(), p.getTaxUsdToVndRate(), p.getTaxRateSource(), p.getTaxableAmountVnd());
        }
    }

    /**
     * The exact ERC-20 {@code transfer(deposit, amount)} the freelancer's own wallet must send, built by the backend (the UI
     * never builds calldata). Present only while a transfer is awaited. FreelaX never signs or sends it.
     */
    public record PreparedTransfer(long chainId, String to, String data, String value) {
        static PreparedTransfer of(AvalanchePayoutRequest p) {
            if (p.getStatus() != AvalanchePayoutStatus.AWAITING_USER_TRANSFER && p.getStatus() != AvalanchePayoutStatus.TRANSFER_FAILED) {
                return null;
            }
            return new PreparedTransfer(p.getChainId(), p.getTokenAddress(), erc20Transfer(p.getDepositAddress(), p.getAmountBaseUnits()), "0");
        }
    }

    static String erc20Transfer(String to, String amountBaseUnits) {
        java.math.BigInteger amount = new java.math.BigInteger(amountBaseUnits);
        if (amount.signum() <= 0 || amount.bitLength() > 256) {
            throw new IllegalArgumentException("amount out of range");
        }
        String address = to.substring(2).toLowerCase(Locale.ROOT);
        return "0xa9059cbb" + "0".repeat(24) + address + String.format("%64s", amount.toString(16)).replace(' ', '0');
    }

    /** {@code payout} is null for UNSUPPORTED_RAIL; {@code reason} says why (stable code). */
    public record RequestResult(Outcome outcome, String reason, PayoutView payout) {
        static RequestResult unsupported(String reason) {
            return new RequestResult(Outcome.UNSUPPORTED_RAIL, reason, null);
        }
    }

    @Transactional
    public RequestResult request(UUID userId, UUID contractId) {
        WorkContract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "contract " + contractId));
        if (!c.getFreelancerId().equals(userId)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        // No silent fallback: a contract on another rail is never routed into (or out of) this adapter.
        if (!gatewayProperties.getNetwork().equals(c.getPaymentNetworkId())) {
            return RequestResult.unsupported("CONTRACT_NOT_ON_AVALANCHE_RAIL");
        }
        if (!properties.isEnabled()) {
            return RequestResult.unsupported("AVALANCHE_PAYOUT_DISABLED");
        }
        Milestone milestone = milestones.findByContractId(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "milestone of " + contractId));
        Optional<AvalanchePayoutRequest> existing = payouts.findByNetworkIdAndMilestoneId(gatewayProperties.getNetwork(), milestone.getId());
        if (existing.isPresent()) {
            return new RequestResult(Outcome.EXISTING, null, PayoutView.of(existing.get()));
        }
        AvalancheResults.Config config = gateway.getConfig();
        if (partner.simulated() && !"LOCAL_SIMULATION".equals(config.environment())) {
            return RequestResult.unsupported("SIMULATED_PARTNER_OUTSIDE_LOCAL_SIMULATION");
        }
        AvalanchePayoutPartner.Capability capability = partner.capability(config.chainId(), config.token());
        if (!capability.supported()) {
            return RequestResult.unsupported(capability.reason() == null ? "PARTNER_CAPABILITY_UNAVAILABLE" : capability.reason());
        }
        String escrowId = AvalancheEscrowIds.fromMilestone(milestone.getId());
        AvalancheResults.Escrow escrow = gateway.findEscrow(escrowId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_DATA, "no Avalanche escrow exists for this contract"));
        if (!"RELEASED".equals(escrow.status())) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "a payout needs an escrow released to the freelancer");
        }
        EvmWallet wallet = evmWallets.findByUserId(userId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_DATA, "link the wallet that received the release first"));
        String from = wallet.getAddress().toLowerCase(Locale.ROOT);
        if (!from.equalsIgnoreCase(escrow.freelancer())) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "the linked wallet is not the wallet the escrow released to");
        }

        UUID id = UUID.randomUUID();
        AvalanchePayoutPartner.Order order = partner.createOrder(new AvalanchePayoutPartner.OrderRequest(id.toString(),
                config.chainId(), config.token(), escrow.amountBaseUnits(), from));
        String deposit = order.depositAddress() == null ? "" : order.depositAddress().toLowerCase(Locale.ROOT);
        if (!ADDRESS.matcher(deposit).matches() || deposit.equals(from) || deposit.equalsIgnoreCase(config.escrow())
                || deposit.equalsIgnoreCase(config.token()) || deposit.equals("0x" + "0".repeat(40))) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "the partner returned an unusable deposit address");
        }
        Instant now = Instant.now();
        AvalanchePayoutRequest p = new AvalanchePayoutRequest();
        p.setId(id);
        p.setContractId(contractId);
        p.setMilestoneId(milestone.getId());
        p.setFreelancerUserId(userId);
        p.setNetworkId(gatewayProperties.getNetwork());
        p.setEnvironment(config.environment());
        p.setChainId(config.chainId());
        p.setTokenAddress(config.token().toLowerCase(Locale.ROOT));
        p.setEscrowId(escrowId);
        p.setAmountBaseUnits(escrow.amountBaseUnits());
        p.setFromAddress(from);
        p.setPartnerId(partner.id());
        p.setPartnerOrderId(order.partnerOrderId());
        p.setDepositAddress(deposit);
        p.setPartnerSimulated(partner.simulated());
        p.setPartnerReportedFee(order.partnerReportedFee());
        p.setPartnerReportedRate(order.partnerReportedRate());
        applySolanaRailFeeAndTax(p, c);
        p.moveTo(AvalanchePayoutStatus.AWAITING_USER_TRANSFER);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        payouts.save(p);
        return new RequestResult(Outcome.CREATED, null, PayoutView.of(p));
    }

    /**
     * Owner decision 2026-11-05: the platform fee and the taxable income follow the existing Solana-rail rules, from the recorded
     * contract budget (the escrowed amount) and the same USD/VND rate provider; labelled simulated. Nothing here moves funds.
     */
    private void applySolanaRailFeeAndTax(AvalanchePayoutRequest p, WorkContract c) {
        if (c.getBudgetUsd() == null) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "the contract has no recorded budget");
        }
        com.marketplace.backend.provider.currency.ExchangeRateResult rate = exchangeRates.getUsdToVndRate();
        java.math.BigDecimal stored = SolanaRailFeeTaxRules.storedTaxRate(rate.rate());
        p.setPlatformFeeUsd(SolanaRailFeeTaxRules.platformFeeUsd(c.getBudgetUsd()));
        p.setTaxUsdToVndRate(stored);
        p.setTaxRateSource(rate.source());
        p.setTaxRateObservedAt(rate.fetchedAt());
        p.setTaxableAmountVnd(SolanaRailFeeTaxRules.taxableAmountVnd(c.getBudgetUsd(), stored));
        p.setTaxTreatment(SolanaRailFeeTaxRules.TREATMENT);
    }

    /** The freelancer reports the hash of the transfer their own wallet sent. Only recorded here; verified on refresh. */
    @Transactional
    public PayoutView recordTransfer(UUID userId, UUID payoutId, String txHash) {
        AvalanchePayoutRequest p = owned(userId, payoutId);
        if (txHash == null || !TX_HASH.matcher(txHash).matches()) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "a 32-byte transaction hash is required");
        }
        String hash = txHash.toLowerCase(Locale.ROOT);
        if (p.getStatus() != AvalanchePayoutStatus.AWAITING_USER_TRANSFER && p.getStatus() != AvalanchePayoutStatus.TRANSFER_FAILED) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "a transfer can only be recorded while one is awaited");
        }
        payouts.findByNetworkIdAndTransferTxHash(p.getNetworkId(), hash).filter(other -> !other.getId().equals(p.getId()))
                .ifPresent(other -> {
                    throw new ApplicationException(ErrorCode.DATA_ALREADY_EXISTS, "this transaction is already used by another payout");
                });
        if (hash.equals(p.getTransferTxHash())) {
            throw new ApplicationException(ErrorCode.INVALID_DATA, "this transaction was already rejected for this payout");
        }
        p.setTransferTxHash(hash);
        p.setTransferChainState(null);
        p.setTransferMatch(null);
        p.setFailureReason(null);
        p.moveTo(AvalanchePayoutStatus.TRANSFER_SUBMITTED);
        p.setUpdatedAt(Instant.now());
        return PayoutView.of(payouts.save(p));
    }

    /**
     * Advances at most one confirmation per stage: on-chain first (FINALIZED and exactly one matching Transfer), then the
     * partner's fiat status. A gateway or partner outage throws and leaves the record unchanged (retry later).
     */
    @Transactional
    public PayoutView refresh(UUID userId, UUID payoutId) {
        AvalanchePayoutRequest p = owned(userId, payoutId);
        if (p.getStatus() == AvalanchePayoutStatus.TRANSFER_SUBMITTED) {
            AvalancheResults.TokenTransfer t = gateway.verifyTokenTransfer(p.getTransferTxHash(), p.getFromAddress(), p.getDepositAddress(),
                    p.getAmountBaseUnits());
            if (t.chainId() != p.getChainId() || t.token() == null || !t.token().equalsIgnoreCase(p.getTokenAddress())) {
                throw new ApplicationException(ErrorCode.INVALID_DATA, "the gateway answered for another chain or token");
            }
            p.setTransferChainState(t.state());
            p.setTransferMatch(t.match());
            if ("FINALIZED".equals(t.state()) && "MATCHED".equals(t.match())) {
                p.moveTo(AvalanchePayoutStatus.TRANSFER_CONFIRMED);
                p.setTransferConfirmedAt(Instant.now());
            } else if ("REVERTED".equals(t.state())) {
                fail(p, AvalanchePayoutStatus.TRANSFER_FAILED, "TRANSFER_REVERTED");
            } else if (("FINALIZED".equals(t.state()) || "UNKNOWN".equals(t.state())) && DEFINITIVE_MISMATCH.contains(t.match())) {
                // FINALIZED with the wrong content, or a receipt of a non-token target (UNKNOWN + TRANSFER_NOT_FOUND)
                fail(p, AvalanchePayoutStatus.TRANSFER_FAILED, "TRANSFER_" + t.match());
            }
            // PENDING, CONFIRMED (not yet final) or no receipt: stay TRANSFER_SUBMITTED
        }
        if (p.getStatus() == AvalanchePayoutStatus.TRANSFER_CONFIRMED || p.getStatus() == AvalanchePayoutStatus.FIAT_PAYOUT_PENDING) {
            if (!partner.id().equals(p.getPartnerId())) {
                // never ask a different partner than the one holding the order (no silent switching)
                p.setFailureReason("PARTNER_CHANGED");
            } else {
                AvalanchePayoutPartner.FiatStatus f = partner.fiatStatus(p.getPartnerOrderId(), p.getTransferTxHash());
                p.setFiatStatus(f.state().name());
                if (f.state() == AvalanchePayoutPartner.FiatState.PAID) {
                    p.moveTo(AvalanchePayoutStatus.FIAT_PAYOUT_CONFIRMED);
                    p.setFiatReference(f.reference());
                    p.setFiatConfirmedAt(Instant.now());
                    p.setFailureReason(null);
                } else if (f.state() == AvalanchePayoutPartner.FiatState.FAILED) {
                    p.setFiatReference(f.reference());
                    fail(p, AvalanchePayoutStatus.FIAT_PAYOUT_FAILED, "PARTNER_REPORTED_FAILURE");
                } else if (p.getStatus() == AvalanchePayoutStatus.TRANSFER_CONFIRMED) {
                    p.moveTo(AvalanchePayoutStatus.FIAT_PAYOUT_PENDING);
                }
            }
        }
        p.setUpdatedAt(Instant.now());
        return PayoutView.of(payouts.save(p));
    }

    @Transactional(readOnly = true)
    public Optional<PayoutView> forContract(UUID userId, UUID contractId) {
        WorkContract c = contracts.findById(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "contract " + contractId));
        if (!c.getFreelancerId().equals(userId)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        return milestones.findByContractId(contractId)
                .flatMap(m -> payouts.findByNetworkIdAndMilestoneId(gatewayProperties.getNetwork(), m.getId()))
                .map(PayoutView::of);
    }

    private AvalanchePayoutRequest owned(UUID userId, UUID payoutId) {
        AvalanchePayoutRequest p = payouts.findWithLockById(payoutId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "payout " + payoutId));
        if (!p.getFreelancerUserId().equals(userId)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        return p;
    }

    private static void fail(AvalanchePayoutRequest p, AvalanchePayoutStatus status, String reason) {
        p.moveTo(status);
        p.setFailureReason(reason);
    }
}
