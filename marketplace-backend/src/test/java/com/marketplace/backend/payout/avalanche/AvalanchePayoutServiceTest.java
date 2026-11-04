package com.marketplace.backend.payout.avalanche;

import com.marketplace.backend.blockchain.avalanche.AvalancheEscrowIds;
import com.marketplace.backend.client.AvalancheClient;
import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.dto.response.avalanche.AvalancheResults;
import com.marketplace.backend.entity.EvmWallet;
import com.marketplace.backend.entity.Milestone;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.AvalancheGatewayException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.EvmWalletRepository;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.marketplace.backend.payout.avalanche.AvalanchePayoutService.Outcome.CREATED;
import static com.marketplace.backend.payout.avalanche.AvalanchePayoutService.Outcome.EXISTING;
import static com.marketplace.backend.payout.avalanche.AvalanchePayoutService.Outcome.UNSUPPORTED_RAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Non-custodial Avalanche payout adapter: rails, capability, eligibility, the two confirmations, failures and replays. */
class AvalanchePayoutServiceTest {
    private static final String NETWORK = "avalanche-local";
    private static final String TOKEN = "0x" + "aa".repeat(20);
    private static final String ESCROW = "0x" + "e5".repeat(20);
    private static final String WALLET = "0x" + "f1".repeat(20);
    private static final String DEPOSIT = "0x" + "de".repeat(20);
    private static final String AMOUNT = "5000000";
    private static final String TX = "0x" + "ab".repeat(32);
    private static final String TX2 = "0x" + "cd".repeat(32);

    private final WorkContractRepository contracts = mock(WorkContractRepository.class);
    private final MilestoneRepository milestones = mock(MilestoneRepository.class);
    private final EvmWalletRepository evmWallets = mock(EvmWalletRepository.class);
    private final AvalanchePayoutRequestRepository payouts = mock(AvalanchePayoutRequestRepository.class);
    private final AvalancheClient gateway = mock(AvalancheClient.class);
    private final Map<UUID, AvalanchePayoutRequest> rows = new HashMap<>();
    private AvalancheGatewayProperties gatewayProperties;
    private AvalanchePayoutProperties properties;
    private AvalanchePayoutPartner partner;
    private UUID freelancer;
    private WorkContract contract;
    private Milestone milestone;

    @BeforeEach
    void setUp() {
        gatewayProperties = new AvalancheGatewayProperties();
        gatewayProperties.setNetwork(NETWORK);
        properties = new AvalanchePayoutProperties();
        properties.setEnabled(true);
        partner = spy(new LocalMockAvalanchePayoutPartner(TOKEN, DEPOSIT, Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC)));
        freelancer = UUID.randomUUID();
        contract = new WorkContract();
        contract.setId(UUID.randomUUID());
        contract.setClientUserId(UUID.randomUUID());
        contract.setFreelancerId(freelancer);
        contract.setPaymentNetworkId(NETWORK);
        milestone = new Milestone();
        milestone.setId(UUID.randomUUID());
        milestone.setContractId(contract.getId());
        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(milestones.findByContractId(contract.getId())).thenReturn(Optional.of(milestone));
        EvmWallet w = new EvmWallet();
        w.setUserId(freelancer);
        w.setAddress(WALLET);
        when(evmWallets.findByUserId(freelancer)).thenReturn(Optional.of(w));
        when(gateway.getConfig()).thenReturn(config("LOCAL_SIMULATION", 31337));
        escrow("RELEASED", WALLET);

        when(payouts.save(any())).thenAnswer(i -> {
            AvalanchePayoutRequest r = i.getArgument(0);
            rows.put(r.getId(), r);
            return r;
        });
        when(payouts.findWithLockById(any())).thenAnswer(i -> Optional.ofNullable(rows.get(i.<UUID>getArgument(0))));
        when(payouts.findByNetworkIdAndMilestoneId(anyString(), any())).thenAnswer(i -> rows.values().stream()
                .filter(r -> r.getNetworkId().equals(i.getArgument(0)) && r.getMilestoneId().equals(i.getArgument(1))).findFirst());
        when(payouts.findByNetworkIdAndTransferTxHash(anyString(), anyString())).thenAnswer(i -> rows.values().stream()
                .filter(r -> r.getNetworkId().equals(i.getArgument(0)) && i.getArgument(1).equals(r.getTransferTxHash())).findFirst());
    }

    private AvalanchePayoutService service() {
        return new AvalanchePayoutService(contracts, milestones, evmWallets, payouts, gateway, gatewayProperties, properties, partner);
    }

    private static AvalancheResults.Config config(String environment, long chainId) {
        return new AvalancheResults.Config(environment, true, chainId, ESCROW, TOKEN, "MockUSDC", "1", false, null, true, null);
    }

    private void escrow(String status, String freelancerAddress) {
        when(gateway.findEscrow(AvalancheEscrowIds.fromMilestone(milestone.getId()))).thenReturn(Optional.of(new AvalancheResults.Escrow(
                "LOCAL_SIMULATION", true, 31337, AvalancheEscrowIds.fromMilestone(milestone.getId()), status, "0x" + "c1".repeat(20),
                freelancerAddress, "0x" + "a1".repeat(20), AMOUNT, 2, 0, 1, 0, 0, null, null)));
    }

    private void chain(String tx, String state, String match) {
        when(gateway.verifyTokenTransfer(tx, WALLET, DEPOSIT, AMOUNT)).thenReturn(new AvalancheResults.TokenTransfer(
                "LOCAL_SIMULATION", true, 31337, tx, TOKEN, WALLET, DEPOSIT, AMOUNT, state, match, 12L));
    }

    private static void assertCode(ErrorCode code, Runnable r) {
        ApplicationException e = assertThrows(ApplicationException.class, r::run);
        assertEquals(code, e.getErrorCode());
    }

    // ---- explicit unsupported results, never a fallback ---------------------------------------------------------

    @Test
    void byDefaultEveryRequestIsAnExplicitUnsupportedRail() {
        properties = new AvalanchePayoutProperties();
        partner = new DisabledAvalanchePayoutPartner();
        AvalanchePayoutService.RequestResult r = service().request(freelancer, contract.getId());
        assertEquals(UNSUPPORTED_RAIL, r.outcome());
        assertEquals("AVALANCHE_PAYOUT_DISABLED", r.reason());
        assertNull(r.payout());
        properties.setEnabled(true);
        r = service().request(freelancer, contract.getId());
        assertEquals(UNSUPPORTED_RAIL, r.outcome());
        assertEquals("PARTNER_NOT_CONFIGURED", r.reason(), "enabled without a partner still pays out nothing");
        verify(payouts, never()).save(any());
    }

    @Test
    void aContractOnAnotherRailIsNeverRoutedHere() {
        contract.setPaymentNetworkId(null); // legacy Solana / fiat rails
        AvalanchePayoutService.RequestResult r = service().request(freelancer, contract.getId());
        assertEquals(UNSUPPORTED_RAIL, r.outcome());
        assertEquals("CONTRACT_NOT_ON_AVALANCHE_RAIL", r.reason());
        contract.setPaymentNetworkId("solana-devnet");
        assertEquals("CONTRACT_NOT_ON_AVALANCHE_RAIL", service().request(freelancer, contract.getId()).reason());
        verifyNoInteractions(gateway);
        verify(payouts, never()).save(any());
    }

    @Test
    void partnerCapabilityIsCheckedForTheExactChainAndToken() {
        when(gateway.getConfig()).thenReturn(new AvalancheResults.Config("LOCAL_SIMULATION", true, 31337, ESCROW,
                "0x" + "bb".repeat(20), "MockUSDC", "1", false, null, true, null));
        AvalanchePayoutService.RequestResult r = service().request(freelancer, contract.getId());
        assertEquals(UNSUPPORTED_RAIL, r.outcome());
        assertEquals("PARTNER_TOKEN_UNSUPPORTED", r.reason());
        verify(partner, never()).createOrder(any());
    }

    @Test
    void theSimulatedPartnerIsRefusedOutsideLocalSimulation() {
        when(gateway.getConfig()).thenReturn(config("AVALANCHE_FUJI", 43113));
        AvalanchePayoutService.RequestResult r = service().request(freelancer, contract.getId());
        assertEquals(UNSUPPORTED_RAIL, r.outcome());
        assertEquals("SIMULATED_PARTNER_OUTSIDE_LOCAL_SIMULATION", r.reason());
        verify(partner, never()).createOrder(any());
    }

    // ---- eligibility ----------------------------------------------------------------------------------------------

    @Test
    void onlyTheContractFreelancerWithTheReleasedWalletMayRequest() {
        assertCode(ErrorCode.ACCESS_DENIED, () -> service().request(contract.getClientUserId(), contract.getId()));
        escrow("FUNDED", WALLET);
        assertCode(ErrorCode.INVALID_DATA, () -> service().request(freelancer, contract.getId()));
        escrow("REFUNDED", WALLET);
        assertCode(ErrorCode.INVALID_DATA, () -> service().request(freelancer, contract.getId()));
        escrow("RELEASED", "0x" + "99".repeat(20));
        assertCode(ErrorCode.INVALID_DATA, () -> service().request(freelancer, contract.getId()));
        escrow("RELEASED", WALLET);
        when(evmWallets.findByUserId(freelancer)).thenReturn(Optional.empty());
        assertCode(ErrorCode.INVALID_DATA, () -> service().request(freelancer, contract.getId()));
        when(gateway.findEscrow(anyString())).thenReturn(Optional.empty());
        assertCode(ErrorCode.INVALID_DATA, () -> service().request(freelancer, contract.getId()));
        verify(payouts, never()).save(any());
    }

    @Test
    void anUnusableDepositAddressFromThePartnerIsRefused() {
        partner = spy(new LocalMockAvalanchePayoutPartner(TOKEN, WALLET, Clock.systemUTC()));
        assertCode(ErrorCode.INVALID_DATA, () -> service().request(freelancer, contract.getId()));
        partner = spy(new LocalMockAvalanchePayoutPartner(TOKEN, ESCROW, Clock.systemUTC()));
        assertCode(ErrorCode.INVALID_DATA, () -> service().request(freelancer, contract.getId()));
        verify(payouts, never()).save(any());
    }

    // ---- the full flow and the two confirmations -------------------------------------------------------------------

    @Test
    void fullFlowKeepsTheOnChainAndFiatConfirmationsApart() {
        AvalanchePayoutService svc = service();
        AvalanchePayoutService.RequestResult created = svc.request(freelancer, contract.getId());
        assertEquals(CREATED, created.outcome());
        AvalanchePayoutService.PayoutView v = created.payout();
        assertEquals(AvalanchePayoutStatus.AWAITING_USER_TRANSFER, v.status());
        assertEquals(AMOUNT, v.amountBaseUnits(), "the released amount, nothing deducted by FreelaX");
        assertEquals(DEPOSIT, v.depositAddress());
        assertEquals(WALLET, v.fromAddress());
        assertTrue(v.partnerSimulated());
        assertNull(v.partnerReportedFee());
        assertEquals("NOT_DEFINED", v.taxTreatment());
        assertTrue(v.notice().startsWith("Demonstration only"));
        assertEquals(EXISTING, svc.request(freelancer, contract.getId()).outcome(), "one payout per milestone");
        // the backend builds the exact transfer the freelancer's wallet sends: transfer(DEPOSIT, 5000000) on the token
        assertEquals(TOKEN, v.preparedTransfer().to());
        assertEquals(31337, v.preparedTransfer().chainId());
        assertEquals("0xa9059cbb" + "0".repeat(24) + "de".repeat(20) + "0".repeat(58) + "4c4b40", v.preparedTransfer().data());

        v = svc.recordTransfer(freelancer, v.id(), TX.toUpperCase().replace("0X", "0x"));
        assertEquals(AvalanchePayoutStatus.TRANSFER_SUBMITTED, v.status());
        assertEquals(TX, v.transferTxHash());
        assertNull(v.preparedTransfer(), "nothing to send once a transfer is recorded");

        chain(TX, "PENDING", "NO_RECEIPT");
        assertEquals(AvalanchePayoutStatus.TRANSFER_SUBMITTED, svc.refresh(freelancer, v.id()).status());
        chain(TX, "CONFIRMED", "MATCHED");
        v = svc.refresh(freelancer, v.id());
        assertEquals(AvalanchePayoutStatus.TRANSFER_SUBMITTED, v.status(), "CONFIRMED is not final: wait for FINALIZED");
        assertFalse(v.onChainTransferConfirmed());
        verify(partner, never()).fiatStatus(anyString(), anyString());

        chain(TX, "FINALIZED", "MATCHED");
        doReturn(new AvalanchePayoutPartner.FiatStatus(AvalanchePayoutPartner.FiatState.PENDING, null)).when(partner).fiatStatus(anyString(), anyString());
        v = svc.refresh(freelancer, v.id());
        assertEquals(AvalanchePayoutStatus.FIAT_PAYOUT_PENDING, v.status());
        assertTrue(v.onChainTransferConfirmed(), "the on-chain transfer is final");
        assertNotNull(v.transferConfirmedAt());
        assertFalse(v.fiatPayoutConfirmed(), "but no fiat payout is confirmed yet");
        assertNull(v.fiatConfirmedAt());

        doCallRealMethod().when(partner).fiatStatus(anyString(), anyString());
        v = svc.refresh(freelancer, v.id());
        assertEquals(AvalanchePayoutStatus.FIAT_PAYOUT_CONFIRMED, v.status());
        assertTrue(v.fiatPayoutConfirmed());
        assertTrue(v.fiatReference().startsWith("SIMULATED-PAYOUT-"));
        assertNotNull(v.fiatConfirmedAt());

        AvalanchePayoutService.PayoutView done = v;
        assertCode(ErrorCode.INVALID_DATA, () -> svc.recordTransfer(freelancer, done.id(), TX2));
        assertEquals(AvalanchePayoutStatus.FIAT_PAYOUT_CONFIRMED, svc.refresh(freelancer, v.id()).status(), "terminal");
    }

    @Test
    void definitiveOnChainFailuresAllowANewTransferButNeverTheSameHashAgain() {
        AvalanchePayoutService svc = service();
        UUID id = svc.request(freelancer, contract.getId()).payout().id();
        svc.recordTransfer(freelancer, id, TX);
        chain(TX, "FINALIZED", "AMOUNT_MISMATCH");
        AvalanchePayoutService.PayoutView v = svc.refresh(freelancer, id);
        assertEquals(AvalanchePayoutStatus.TRANSFER_FAILED, v.status());
        assertEquals("TRANSFER_AMOUNT_MISMATCH", v.failureReason());
        assertCode(ErrorCode.INVALID_DATA, () -> svc.recordTransfer(freelancer, id, TX));

        svc.recordTransfer(freelancer, id, TX2);
        chain(TX2, "REVERTED", "TRANSFER_NOT_FOUND");
        assertEquals("TRANSFER_REVERTED", svc.refresh(freelancer, id).failureReason());

        String tx3 = "0x" + "ef".repeat(32);
        svc.recordTransfer(freelancer, id, tx3);
        chain(tx3, "UNKNOWN", "TRANSFER_NOT_FOUND"); // a receipt for some other contract
        assertEquals("TRANSFER_TRANSFER_NOT_FOUND", svc.refresh(freelancer, id).failureReason());

        String tx4 = "0x" + "12".repeat(32);
        svc.recordTransfer(freelancer, id, tx4);
        chain(tx4, "UNKNOWN", "NO_RECEIPT");
        assertEquals(AvalanchePayoutStatus.TRANSFER_SUBMITTED, svc.refresh(freelancer, id).status(), "no receipt proves nothing");
        chain(tx4, "FINALIZED", "MATCHED");
        assertEquals(AvalanchePayoutStatus.FIAT_PAYOUT_CONFIRMED, svc.refresh(freelancer, id).status());
    }

    @Test
    void aTransferHashCannotServeTwoPayouts() {
        AvalanchePayoutService svc = service();
        UUID first = svc.request(freelancer, contract.getId()).payout().id();
        svc.recordTransfer(freelancer, first, TX);
        // a second contract of the same freelancer
        WorkContract other = new WorkContract();
        other.setId(UUID.randomUUID());
        other.setFreelancerId(freelancer);
        other.setPaymentNetworkId(NETWORK);
        Milestone m2 = new Milestone();
        m2.setId(UUID.randomUUID());
        when(contracts.findById(other.getId())).thenReturn(Optional.of(other));
        when(milestones.findByContractId(other.getId())).thenReturn(Optional.of(m2));
        when(gateway.findEscrow(AvalancheEscrowIds.fromMilestone(m2.getId()))).thenReturn(Optional.of(new AvalancheResults.Escrow(
                "LOCAL_SIMULATION", true, 31337, "x", "RELEASED", "0x" + "c1".repeat(20), WALLET, null, AMOUNT, 2, 0, 1, 0, 0, null, null)));
        UUID second = svc.request(freelancer, other.getId()).payout().id();
        assertCode(ErrorCode.DATA_ALREADY_EXISTS, () -> svc.recordTransfer(freelancer, second, TX));
    }

    @Test
    void fiatFailureIsTerminalAndDoesNotUndoTheOnChainConfirmation() {
        AvalanchePayoutService svc = service();
        UUID id = svc.request(freelancer, contract.getId()).payout().id();
        svc.recordTransfer(freelancer, id, TX);
        chain(TX, "FINALIZED", "MATCHED");
        doReturn(new AvalanchePayoutPartner.FiatStatus(AvalanchePayoutPartner.FiatState.FAILED, "SIMULATED-REJECTED"))
                .when(partner).fiatStatus(anyString(), anyString());
        AvalanchePayoutService.PayoutView v = svc.refresh(freelancer, id);
        assertEquals(AvalanchePayoutStatus.FIAT_PAYOUT_FAILED, v.status());
        assertEquals("PARTNER_REPORTED_FAILURE", v.failureReason());
        assertTrue(v.onChainTransferConfirmed());
        assertFalse(v.fiatPayoutConfirmed());
    }

    @Test
    void aGatewayOutageChangesNothingAndAnAnswerForAnotherTokenIsRefused() {
        AvalanchePayoutService svc = service();
        UUID id = svc.request(freelancer, contract.getId()).payout().id();
        svc.recordTransfer(freelancer, id, TX);
        when(gateway.verifyTokenTransfer(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new AvalancheGatewayException("verify-token-transfer: gateway unreachable", false, null, null));
        assertThrows(AvalancheGatewayException.class, () -> svc.refresh(freelancer, id));
        assertEquals(AvalanchePayoutStatus.TRANSFER_SUBMITTED, rows.get(id).getStatus());

        doReturn(new AvalancheResults.TokenTransfer("LOCAL_SIMULATION", true, 31337, TX, "0x" + "bb".repeat(20), WALLET, DEPOSIT, AMOUNT,
                "FINALIZED", "MATCHED", 1L)).when(gateway).verifyTokenTransfer(anyString(), anyString(), anyString(), anyString());
        assertCode(ErrorCode.INVALID_DATA, () -> svc.refresh(freelancer, id));
        assertEquals(AvalanchePayoutStatus.TRANSFER_SUBMITTED, rows.get(id).getStatus());
    }

    @Test
    void aChangedPartnerIsNeverAskedAboutAnotherPartnersOrder() {
        AvalanchePayoutService svc = service();
        UUID id = svc.request(freelancer, contract.getId()).payout().id();
        svc.recordTransfer(freelancer, id, TX);
        chain(TX, "FINALIZED", "MATCHED");
        partner = mock(AvalanchePayoutPartner.class);
        when(partner.id()).thenReturn("another-partner");
        AvalanchePayoutService.PayoutView v = service().refresh(freelancer, id);
        assertEquals(AvalanchePayoutStatus.TRANSFER_CONFIRMED, v.status());
        assertEquals("PARTNER_CHANGED", v.failureReason());
        verify(partner, never()).fiatStatus(anyString(), anyString());
    }

    @Test
    void onlyTheOwnerReadsOrAdvancesAPayout() {
        AvalanchePayoutService svc = service();
        UUID id = svc.request(freelancer, contract.getId()).payout().id();
        UUID stranger = UUID.randomUUID();
        assertCode(ErrorCode.ACCESS_DENIED, () -> svc.recordTransfer(stranger, id, TX));
        assertCode(ErrorCode.ACCESS_DENIED, () -> svc.refresh(stranger, id));
        assertCode(ErrorCode.ACCESS_DENIED, () -> svc.forContract(contract.getClientUserId(), contract.getId()));
        assertEquals(id, svc.forContract(freelancer, contract.getId()).orElseThrow().id());
        assertCode(ErrorCode.INVALID_DATA, () -> svc.recordTransfer(freelancer, id, "0x12"));
        assertCode(ErrorCode.DATA_NOT_FOUND, () -> svc.refresh(freelancer, UUID.randomUUID()));
    }
}
