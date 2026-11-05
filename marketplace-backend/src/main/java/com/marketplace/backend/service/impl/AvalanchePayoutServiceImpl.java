package com.marketplace.backend.service.impl;

import com.marketplace.backend.blockchain.avalanche.AvalancheEscrowIds;
import com.marketplace.backend.client.AvalancheClient;
import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.configuration.AvalanchePayoutProperties;
import com.marketplace.backend.dto.response.avalanche.AvalanchePayoutOutcome;
import com.marketplace.backend.dto.response.avalanche.AvalanchePayoutResult;
import com.marketplace.backend.dto.response.avalanche.AvalanchePayoutView;
import com.marketplace.backend.dto.response.avalanche.GatewayConfigResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayEscrowResponse;
import com.marketplace.backend.dto.response.avalanche.GatewayTokenTransferResponse;
import com.marketplace.backend.entity.AvalanchePayoutRequest;
import com.marketplace.backend.entity.AvalanchePayoutStatus;
import com.marketplace.backend.entity.EvmWallet;
import com.marketplace.backend.entity.Milestone;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.mapper.AvalanchePayoutMapper;
import com.marketplace.backend.payout.avalanche.AvalanchePayoutPartner;
import com.marketplace.backend.payout.avalanche.SolanaRailFeeTaxRules;
import com.marketplace.backend.provider.currency.ExchangeRateProvider;
import com.marketplace.backend.provider.currency.ExchangeRateResult;
import com.marketplace.backend.repository.AvalanchePayoutRequestRepository;
import com.marketplace.backend.repository.EvmWalletRepository;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import com.marketplace.backend.service.AvalanchePayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fee and tax (owner decision 2026-11-05): the existing Solana-rail rules are applied unchanged as SIMULATED accounting figures
 * ({@link SolanaRailFeeTaxRules}); they never change the amount the freelancer's wallet transfers, and no withheld tax is computed
 * (MISA reports it on the Solana rail; no MISA record is created for this rail).
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "avalanche-gateway.enabled", havingValue = "true")
public class AvalanchePayoutServiceImpl implements AvalanchePayoutService {

    private static final String LOCAL_SIMULATION = "LOCAL_SIMULATION";
    private static final String RELEASED = "RELEASED";
    private static final String STATE_FINALIZED = "FINALIZED";
    private static final String STATE_REVERTED = "REVERTED";
    private static final String STATE_UNKNOWN = "UNKNOWN";
    private static final String MATCH_MATCHED = "MATCHED";
    private static final String REASON_TRANSFER_REVERTED = "TRANSFER_REVERTED";
    private static final String REASON_PARTNER_CHANGED = "PARTNER_CHANGED";
    private static final String REASON_PARTNER_FAILURE = "PARTNER_REPORTED_FAILURE";
    private static final Pattern TX_HASH = Pattern.compile("0x[0-9a-fA-F]{64}");
    private static final Pattern ADDRESS = Pattern.compile("0x[0-9a-fA-F]{40}");
    private static final String ZERO_ADDRESS = "0x" + "0".repeat(40);
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
    private final ExchangeRateProvider exchangeRates;
    private final AvalanchePayoutMapper mapper;

    @Override
    @Transactional
    public AvalanchePayoutResult request(UUID userId, UUID contractId) {
        WorkContract contract = freelancerContract(userId, contractId);
        // No silent fallback: a contract on another rail is never routed into (or out of) this adapter.
        if (!gatewayProperties.getNetwork().equals(contract.getPaymentNetworkId())) {
            return AvalanchePayoutResult.unsupported("CONTRACT_NOT_ON_AVALANCHE_RAIL");
        }
        if (!properties.isEnabled()) {
            return AvalanchePayoutResult.unsupported("AVALANCHE_PAYOUT_DISABLED");
        }
        Milestone milestone = milestoneOf(contractId);
        Optional<AvalanchePayoutRequest> existing = payouts.findByNetworkIdAndMilestoneId(gatewayProperties.getNetwork(), milestone.getId());
        if (existing.isPresent()) {
            return new AvalanchePayoutResult(AvalanchePayoutOutcome.EXISTING, null, mapper.toView(existing.get()));
        }
        GatewayConfigResponse config = gateway.getConfig();
        Optional<String> unsupportedReason = partnerRefusal(config);
        if (unsupportedReason.isPresent()) {
            return AvalanchePayoutResult.unsupported(unsupportedReason.get());
        }
        AvalanchePayoutRequest created = createOrder(userId, contract, milestone, config);
        payouts.save(created);
        return new AvalanchePayoutResult(AvalanchePayoutOutcome.CREATED, null, mapper.toView(created));
    }

    @Override
    @Transactional
    public AvalanchePayoutView recordTransfer(UUID userId, UUID payoutId, String txHash) {
        AvalanchePayoutRequest p = owned(userId, payoutId);
        if (txHash == null || !TX_HASH.matcher(txHash).matches()) {
            throw invalid("a 32-byte transaction hash is required");
        }
        String hash = txHash.toLowerCase(Locale.ROOT);
        if (!awaitsTransfer(p)) {
            throw invalid("a transfer can only be recorded while one is awaited");
        }
        payouts.findByNetworkIdAndTransferTxHash(p.getNetworkId(), hash).filter(other -> !other.getId().equals(p.getId()))
                .ifPresent(other -> {
                    throw new ApplicationException(ErrorCode.DATA_ALREADY_EXISTS, "this transaction is already used by another payout");
                });
        if (hash.equals(p.getTransferTxHash())) {
            throw invalid("this transaction was already rejected for this payout");
        }
        p.setTransferTxHash(hash);
        p.setTransferChainState(null);
        p.setTransferMatch(null);
        p.setFailureReason(null);
        p.moveTo(AvalanchePayoutStatus.TRANSFER_SUBMITTED);
        p.setUpdatedAt(Instant.now());
        return mapper.toView(payouts.save(p));
    }

    @Override
    @Transactional
    public AvalanchePayoutView refresh(UUID userId, UUID payoutId) {
        AvalanchePayoutRequest p = owned(userId, payoutId);
        if (p.getStatus() == AvalanchePayoutStatus.TRANSFER_SUBMITTED) {
            verifyOnChainTransfer(p);
        }
        if (p.getStatus() == AvalanchePayoutStatus.TRANSFER_CONFIRMED || p.getStatus() == AvalanchePayoutStatus.FIAT_PAYOUT_PENDING) {
            askPartnerForFiatStatus(p);
        }
        p.setUpdatedAt(Instant.now());
        return mapper.toView(payouts.save(p));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AvalanchePayoutView> forContract(UUID userId, UUID contractId) {
        freelancerContract(userId, contractId);
        return milestones.findByContractId(contractId)
                .flatMap(m -> payouts.findByNetworkIdAndMilestoneId(gatewayProperties.getNetwork(), m.getId()))
                .map(mapper::toView);
    }

    /** Why this partner cannot serve the gateway's environment, chain and token; empty when it can. */
    private Optional<String> partnerRefusal(GatewayConfigResponse config) {
        if (partner.simulated() && !LOCAL_SIMULATION.equals(config.environment())) {
            return Optional.of("SIMULATED_PARTNER_OUTSIDE_LOCAL_SIMULATION");
        }
        AvalanchePayoutPartner.Capability capability = partner.capability(config.chainId(), config.token());
        if (capability.supported()) {
            return Optional.empty();
        }
        return Optional.of(capability.reason() == null ? "PARTNER_CAPABILITY_UNAVAILABLE" : capability.reason());
    }

    private AvalanchePayoutRequest createOrder(UUID userId, WorkContract contract, Milestone milestone, GatewayConfigResponse config) {
        String escrowId = AvalancheEscrowIds.fromMilestone(milestone.getId());
        GatewayEscrowResponse escrow = releasedEscrow(escrowId);
        String from = releaseWallet(userId, escrow);

        UUID id = UUID.randomUUID();
        AvalanchePayoutPartner.Order order = partner.createOrder(new AvalanchePayoutPartner.OrderRequest(id.toString(),
                config.chainId(), config.token(), escrow.amountBaseUnits(), from));
        String deposit = usableDepositAddress(order, from, config);

        Instant now = Instant.now();
        AvalanchePayoutRequest p = new AvalanchePayoutRequest();
        p.setId(id);
        p.setContractId(contract.getId());
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
        applySolanaRailFeeAndTax(p, contract);
        p.moveTo(AvalanchePayoutStatus.AWAITING_USER_TRANSFER);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return p;
    }

    private GatewayEscrowResponse releasedEscrow(String escrowId) {
        GatewayEscrowResponse escrow = gateway.findEscrow(escrowId)
                .orElseThrow(() -> invalid("no Avalanche escrow exists for this contract"));
        if (!RELEASED.equals(escrow.status())) {
            throw invalid("a payout needs an escrow released to the freelancer");
        }
        return escrow;
    }

    /** The linked wallet of the freelancer, which must be the wallet the escrow released to. */
    private String releaseWallet(UUID userId, GatewayEscrowResponse escrow) {
        EvmWallet wallet = evmWallets.findByUserId(userId)
                .orElseThrow(() -> invalid("link the wallet that received the release first"));
        String from = wallet.getAddress().toLowerCase(Locale.ROOT);
        if (!from.equalsIgnoreCase(escrow.freelancer())) {
            throw invalid("the linked wallet is not the wallet the escrow released to");
        }
        return from;
    }

    private String usableDepositAddress(AvalanchePayoutPartner.Order order, String from, GatewayConfigResponse config) {
        String deposit = order.depositAddress() == null ? "" : order.depositAddress().toLowerCase(Locale.ROOT);
        boolean usable = ADDRESS.matcher(deposit).matches() && !deposit.equals(from) && !deposit.equalsIgnoreCase(config.escrow())
                && !deposit.equalsIgnoreCase(config.token()) && !deposit.equals(ZERO_ADDRESS);
        if (!usable) {
            throw invalid("the partner returned an unusable deposit address");
        }
        return deposit;
    }

    /**
     * Owner decision 2026-11-05: the platform fee and the taxable income follow the existing Solana-rail rules, from the recorded
     * contract budget (the escrowed amount) and the same USD/VND rate provider; labelled simulated. Nothing here moves funds.
     */
    private void applySolanaRailFeeAndTax(AvalanchePayoutRequest p, WorkContract contract) {
        BigDecimal budget = contract.getBudgetUsd();
        if (budget == null) {
            throw invalid("the contract has no recorded budget");
        }
        ExchangeRateResult rate = exchangeRates.getUsdToVndRate();
        BigDecimal stored = SolanaRailFeeTaxRules.storedTaxRate(rate.rate());
        p.setPlatformFeeUsd(SolanaRailFeeTaxRules.platformFeeUsd(budget));
        p.setTaxUsdToVndRate(stored);
        p.setTaxRateSource(rate.source());
        p.setTaxRateObservedAt(rate.fetchedAt());
        p.setTaxableAmountVnd(SolanaRailFeeTaxRules.taxableAmountVnd(budget, stored));
        p.setTaxTreatment(SolanaRailFeeTaxRules.TREATMENT);
    }

    private void verifyOnChainTransfer(AvalanchePayoutRequest p) {
        GatewayTokenTransferResponse t = gateway.verifyTokenTransfer(p.getTransferTxHash(), p.getFromAddress(), p.getDepositAddress(),
                p.getAmountBaseUnits());
        if (t.chainId() != p.getChainId() || t.token() == null || !t.token().equalsIgnoreCase(p.getTokenAddress())) {
            throw invalid("the gateway answered for another chain or token");
        }
        p.setTransferChainState(t.state());
        p.setTransferMatch(t.match());
        if (STATE_FINALIZED.equals(t.state()) && MATCH_MATCHED.equals(t.match())) {
            p.moveTo(AvalanchePayoutStatus.TRANSFER_CONFIRMED);
            p.setTransferConfirmedAt(Instant.now());
        } else if (STATE_REVERTED.equals(t.state())) {
            fail(p, AvalanchePayoutStatus.TRANSFER_FAILED, REASON_TRANSFER_REVERTED);
        } else if (isFinalWithWrongContent(t)) {
            fail(p, AvalanchePayoutStatus.TRANSFER_FAILED, "TRANSFER_" + t.match());
        }
        // PENDING, CONFIRMED (not yet final) or no receipt: stay TRANSFER_SUBMITTED
    }

    /** FINALIZED with the wrong content, or a receipt of a non-token target (UNKNOWN + TRANSFER_NOT_FOUND). */
    private static boolean isFinalWithWrongContent(GatewayTokenTransferResponse t) {
        boolean decided = STATE_FINALIZED.equals(t.state()) || STATE_UNKNOWN.equals(t.state());
        return decided && DEFINITIVE_MISMATCH.contains(t.match());
    }

    private void askPartnerForFiatStatus(AvalanchePayoutRequest p) {
        if (!partner.id().equals(p.getPartnerId())) {
            // never ask a different partner than the one holding the order (no silent switching)
            p.setFailureReason(REASON_PARTNER_CHANGED);
            return;
        }
        AvalanchePayoutPartner.FiatStatus f = partner.fiatStatus(p.getPartnerOrderId(), p.getTransferTxHash());
        p.setFiatStatus(f.state().name());
        if (f.state() == AvalanchePayoutPartner.FiatState.PAID) {
            p.moveTo(AvalanchePayoutStatus.FIAT_PAYOUT_CONFIRMED);
            p.setFiatReference(f.reference());
            p.setFiatConfirmedAt(Instant.now());
            p.setFailureReason(null);
        } else if (f.state() == AvalanchePayoutPartner.FiatState.FAILED) {
            p.setFiatReference(f.reference());
            fail(p, AvalanchePayoutStatus.FIAT_PAYOUT_FAILED, REASON_PARTNER_FAILURE);
        } else if (p.getStatus() == AvalanchePayoutStatus.TRANSFER_CONFIRMED) {
            p.moveTo(AvalanchePayoutStatus.FIAT_PAYOUT_PENDING);
        }
    }

    private WorkContract freelancerContract(UUID userId, UUID contractId) {
        WorkContract contract = contracts.findById(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "contract " + contractId));
        if (!contract.getFreelancerId().equals(userId)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        return contract;
    }

    private Milestone milestoneOf(UUID contractId) {
        return milestones.findByContractId(contractId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "milestone of " + contractId));
    }

    private AvalanchePayoutRequest owned(UUID userId, UUID payoutId) {
        AvalanchePayoutRequest p = payouts.findWithLockById(payoutId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.DATA_NOT_FOUND, "payout " + payoutId));
        if (!p.getFreelancerUserId().equals(userId)) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        return p;
    }

    private static boolean awaitsTransfer(AvalanchePayoutRequest p) {
        return p.getStatus() == AvalanchePayoutStatus.AWAITING_USER_TRANSFER || p.getStatus() == AvalanchePayoutStatus.TRANSFER_FAILED;
    }

    private static void fail(AvalanchePayoutRequest p, AvalanchePayoutStatus status, String reason) {
        p.moveTo(status);
        p.setFailureReason(reason);
    }

    private static ApplicationException invalid(String message) {
        return new ApplicationException(ErrorCode.INVALID_DATA, message);
    }
}
