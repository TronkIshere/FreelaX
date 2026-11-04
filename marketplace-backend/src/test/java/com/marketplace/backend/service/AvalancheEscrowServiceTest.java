package com.marketplace.backend.service;

import com.marketplace.backend.blockchain.AssetReference;
import com.marketplace.backend.blockchain.BlockchainAddress;
import com.marketplace.backend.blockchain.BlockchainGateway;
import com.marketplace.backend.blockchain.BlockchainGatewayRegistry;
import com.marketplace.backend.blockchain.BlockchainNetwork;
import com.marketplace.backend.blockchain.EnvironmentInfo;
import com.marketplace.backend.blockchain.EscrowActionRequest;
import com.marketplace.backend.blockchain.EscrowReference;
import com.marketplace.backend.blockchain.EscrowSnapshot;
import com.marketplace.backend.blockchain.NeutralEscrowStatus;
import com.marketplace.backend.blockchain.TransactionReference;
import com.marketplace.backend.blockchain.TransactionResult;
import com.marketplace.backend.blockchain.TransactionStatus;
import com.marketplace.backend.blockchain.avalanche.AvalancheEscrowIds;
import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.entity.AvalancheChainTransaction;
import com.marketplace.backend.entity.ContractDispute;
import com.marketplace.backend.entity.ContractStatus;
import com.marketplace.backend.entity.DisputeStatus;
import com.marketplace.backend.entity.EscrowContract;
import com.marketplace.backend.entity.JobSubmission;
import com.marketplace.backend.entity.JobSubmissionStatus;
import com.marketplace.backend.entity.Milestone;
import com.marketplace.backend.entity.MilestoneStatus;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.AvalancheGatewayException;
import com.marketplace.backend.repository.AvalancheChainTransactionRepository;
import com.marketplace.backend.repository.ContractDisputeRepository;
import com.marketplace.backend.repository.EscrowContractRepository;
import com.marketplace.backend.repository.JobSubmissionRepository;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.WalletRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvalancheEscrowServiceTest {
    private static final String NETWORK = "avalanche-local";
    private static final String CLIENT_WALLET = "0x" + "c1".repeat(20);
    private static final String FREELANCER_WALLET = "0x" + "f2".repeat(20);

    private final WorkContractRepository contracts = mock(WorkContractRepository.class);
    private final MilestoneRepository milestones = mock(MilestoneRepository.class);
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final JobSubmissionRepository submissions = mock(JobSubmissionRepository.class);
    private final ContractDisputeRepository disputes = mock(ContractDisputeRepository.class);
    private final EscrowContractRepository solanaEscrows = mock(EscrowContractRepository.class);
    private final AvalancheChainTransactionRepository chainTransactions = mock(AvalancheChainTransactionRepository.class);
    private final BlockchainGateway gateway = mock(BlockchainGateway.class);
    private final com.marketplace.backend.repository.EvmWalletRepository evmWallets = mock(com.marketplace.backend.repository.EvmWalletRepository.class);
    private final com.marketplace.backend.repository.AvalanchePendingAuthorizationRepository pendingAuthorizations =
            mock(com.marketplace.backend.repository.AvalanchePendingAuthorizationRepository.class);
    private final java.util.List<com.marketplace.backend.entity.AvalanchePendingAuthorization> pendingRows = new java.util.ArrayList<>();
    private final AvalancheGatewayProperties properties = new AvalancheGatewayProperties();
    private AvalancheEscrowService service;

    private final UUID clientId = UUID.randomUUID();
    private final UUID freelancerId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();
    private WorkContract contract;
    private Milestone milestone;
    private final List<AvalancheChainTransaction> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        properties.setNetwork(NETWORK);
        when(gateway.network()).thenReturn(new BlockchainNetwork(NETWORK));
        when(gateway.environmentInfo()).thenReturn(new EnvironmentInfo("LOCAL_SIMULATION", 31337, true, null, true));
        when(gateway.validateAddress(any(BlockchainAddress.class))).thenReturn(true);
        service = new AvalancheEscrowService(contracts, milestones, wallets, submissions, disputes, solanaEscrows, chainTransactions,
                new BlockchainGatewayRegistry(List.of(gateway)), properties, evmWallets, pendingAuthorizations);

        contract = new WorkContract();
        contract.setId(UUID.randomUUID());
        contract.setClientUserId(clientId);
        contract.setFreelancerId(freelancerId);
        contract.setBudgetUsd(new BigDecimal("5.00"));
        contract.setDeliveryDueAt(Instant.now().plusSeconds(86_400 * 7));
        contract.setReviewWindowHours(72);
        contract.setMaxRevisions(2);
        contract.setStatus(ContractStatus.PENDING_FUNDING);
        contract.setPaymentNetworkId(NETWORK);
        milestone = new Milestone();
        milestone.setId(UUID.randomUUID());
        milestone.setContractId(contract.getId());
        milestone.setAmount(new BigDecimal("5.00"));
        milestone.setStatus(MilestoneStatus.PENDING_FUNDING);

        when(contracts.findById(contract.getId())).thenReturn(Optional.of(contract));
        when(evmWallets.findByUserId(any())).thenReturn(Optional.empty());
        org.mockito.stubbing.Answer<Optional<com.marketplace.backend.entity.AvalanchePendingAuthorization>> findPending = invocation ->
                pendingRows.stream().filter(a -> a.getContractId().equals(invocation.getArgument(0))
                        && a.getAction().equals(invocation.getArgument(1))).findFirst();
        when(pendingAuthorizations.findByContractIdAndAction(any(), anyString())).thenAnswer(findPending);
        when(pendingAuthorizations.findWithLockByContractIdAndAction(any(), anyString())).thenAnswer(findPending);
        when(pendingAuthorizations.saveAndFlush(any(com.marketplace.backend.entity.AvalanchePendingAuthorization.class))).thenAnswer(invocation -> {
            com.marketplace.backend.entity.AvalanchePendingAuthorization a = invocation.getArgument(0);
            if (!pendingRows.contains(a)) {
                pendingRows.add(a);
            }
            return a;
        });
        org.mockito.Mockito.doAnswer(invocation -> pendingRows.remove(invocation.<Object>getArgument(0)))
                .when(pendingAuthorizations).delete(any(com.marketplace.backend.entity.AvalanchePendingAuthorization.class));
        when(milestones.findByContractId(contract.getId())).thenReturn(Optional.of(milestone));
        when(wallets.findFirstByUserIdOrderByIdAsc(clientId)).thenReturn(Optional.of(wallet(CLIENT_WALLET)));
        when(wallets.findFirstByUserIdOrderByIdAsc(freelancerId)).thenReturn(Optional.of(wallet(FREELANCER_WALLET)));
        when(solanaEscrows.findByContractId(contract.getId())).thenReturn(Optional.empty());
        when(chainTransactions.findByNetworkIdAndOperationKey(anyString(), anyString())).thenAnswer(invocation ->
                rows.stream().filter(r -> r.getOperationKey().equals(invocation.getArgument(1))).findFirst());
        when(chainTransactions.findByContractIdOrderByIdAsc(contract.getId())).thenAnswer(invocation -> List.copyOf(rows));
        when(chainTransactions.saveAndFlush(any(AvalancheChainTransaction.class))).thenAnswer(invocation -> {
            AvalancheChainTransaction row = invocation.getArgument(0);
            if (!rows.contains(row)) {
                rows.add(row);
            }
            return row;
        });
        when(chainTransactions.save(any(AvalancheChainTransaction.class))).thenAnswer(invocation -> {
            AvalancheChainTransaction row = invocation.getArgument(0);
            if (!rows.contains(row)) {
                rows.add(row);
            }
            return row;
        });
    }

    private static Wallet wallet(String address) {
        Wallet wallet = new Wallet();
        wallet.setPublicKey(address);
        return wallet;
    }

    private static String sha(String value) throws Exception {
        return "0x" + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static void assertRefused(Runnable action) {
        assertThrows(ApplicationException.class, action::run);
    }

    // ---- choosing the rail --------------------------------------------------------------------------------------------

    @Test
    void onlyTheClientMayChooseTheRailAndNeverOnASolanaContractOrAfterFunding() {
        contract.setPaymentNetworkId(null);
        assertRefused(() -> service.enable(freelancerId, contract.getId()));
        assertRefused(() -> service.enable(strangerId, contract.getId()));

        when(solanaEscrows.findByContractId(contract.getId())).thenReturn(Optional.of(new EscrowContract()));
        assertRefused(() -> service.enable(clientId, contract.getId()));
        assertNull(contract.getPaymentNetworkId(), "a contract with a Solana escrow is never moved");

        when(solanaEscrows.findByContractId(contract.getId())).thenReturn(Optional.empty());
        contract.setStatus(ContractStatus.ACTIVE);
        assertRefused(() -> service.enable(clientId, contract.getId()));

        contract.setStatus(ContractStatus.PENDING_FUNDING);
        service.enable(clientId, contract.getId());
        assertEquals(NETWORK, contract.getPaymentNetworkId());
    }

    @Test
    void aContractWithoutTheAvalancheRailCannotUseTheService() {
        contract.setPaymentNetworkId(null);
        assertRefused(() -> service.execute(clientId, false, contract.getId(), "fund", null));
        contract.setPaymentNetworkId("localnet");
        assertRefused(() -> service.prepare(clientId, false, contract.getId(), "fund", null));
        verify(gateway, never()).executeAction(any());
    }

    // ---- terms come only from recorded data ----------------------------------------------------------------------------

    @Test
    void fundTermsAreDerivedFromTheRecordsAndTheRequestCannotCarryAny() {
        Map<String, Object> params = service.authorize(clientId, false, contract, "fund", AvalancheEscrowService.Input.empty());
        assertEquals(CLIENT_WALLET, params.get("client"));
        assertEquals(FREELANCER_WALLET, params.get("freelancer"));
        assertEquals("5000000", params.get("amount"));
        assertEquals(contract.getDeliveryDueAt().getEpochSecond(), params.get("deliveryDueAt"));
        assertEquals(72, params.get("reviewWindowHours"));
        assertEquals(2, params.get("maxRevisions"));
        assertEquals(false, params.get("highValueGrace"));
        long fundingExpiry = (Long) params.get("fundingExpiresAt");
        assertTrue(fundingExpiry > Instant.now().plusSeconds(47 * 3600).getEpochSecond() && fundingExpiry < Instant.now().plusSeconds(49 * 3600).getEpochSecond());
        List<String> inputFields = java.util.Arrays.stream(AvalancheEscrowService.Input.class.getRecordComponents()).map(c -> c.getName()).toList();
        for (String forbidden : List.of("amount", "client", "freelancer", "deliveryDueAt", "reviewWindowHours", "maxRevisions", "arbiter", "chainId", "rpcUrl", "privateKey")) {
            assertFalse(inputFields.contains(forbidden), "the caller cannot supply " + forbidden);
        }
    }

    @Test
    void fundIsRefusedForTheWrongRoleStateWalletsAmountOrReviewWindow() {
        AvalancheEscrowService.Input none = AvalancheEscrowService.Input.empty();
        assertRefused(() -> service.authorize(freelancerId, false, contract, "fund", none));
        assertRefused(() -> service.authorize(strangerId, false, contract, "fund", none));
        assertRefused(() -> service.authorize(strangerId, true, contract, "fund", none));
        contract.setStatus(ContractStatus.ACTIVE);
        assertRefused(() -> service.authorize(clientId, false, contract, "fund", none));
        contract.setStatus(ContractStatus.PENDING_FUNDING);
        milestone.setAmount(new BigDecimal("6.00"));
        assertRefused(() -> service.authorize(clientId, false, contract, "fund", none));
        milestone.setAmount(new BigDecimal("5.00"));
        contract.setReviewWindowHours(24);
        assertRefused(() -> service.authorize(clientId, false, contract, "fund", none));
        contract.setReviewWindowHours(72);
        when(wallets.findFirstByUserIdOrderByIdAsc(freelancerId)).thenReturn(Optional.empty());
        assertRefused(() -> service.authorize(clientId, false, contract, "fund", none));
        when(wallets.findFirstByUserIdOrderByIdAsc(freelancerId)).thenReturn(Optional.of(wallet(FREELANCER_WALLET)));
        when(gateway.validateAddress(new BlockchainAddress(FREELANCER_WALLET))).thenReturn(false);
        assertRefused(() -> service.authorize(clientId, false, contract, "fund", none));
    }

    // ---- hashes come only from recorded rows ---------------------------------------------------------------------------

    private JobSubmission submission(JobSubmissionStatus status, UUID forContract) {
        JobSubmission s = new JobSubmission();
        s.setId(UUID.randomUUID());
        s.setContractId(forContract);
        s.setPayloadHash("payload-hash");
        s.setStatus(status);
        when(submissions.findById(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void submitAnchorsOnlyARecordedSubmissionOfThisContractInReview() throws Exception {
        contract.setStatus(ContractStatus.ACTIVE);
        JobSubmission recorded = submission(JobSubmissionStatus.SUBMITTED, contract.getId());
        Map<String, Object> params = service.authorize(freelancerId, false, contract, "submit",
                new AvalancheEscrowService.Input(null, recorded.getId(), null, null, null, null, null));
        assertEquals(sha("avalanche-evidence:" + contract.getId() + ":" + recorded.getId() + ":payload-hash"), params.get("evidenceHash"));

        JobSubmission foreign = submission(JobSubmissionStatus.SUBMITTED, UUID.randomUUID());
        assertRefused(() -> service.authorize(freelancerId, false, contract, "submit",
                new AvalancheEscrowService.Input(null, foreign.getId(), null, null, null, null, null)));
        JobSubmission approved = submission(JobSubmissionStatus.APPROVED, contract.getId());
        assertRefused(() -> service.authorize(freelancerId, false, contract, "submit",
                new AvalancheEscrowService.Input(null, approved.getId(), null, null, null, null, null)));
        assertRefused(() -> service.authorize(freelancerId, false, contract, "submit", AvalancheEscrowService.Input.empty()));
        assertRefused(() -> service.authorize(clientId, false, contract, "submit",
                new AvalancheEscrowService.Input(null, recorded.getId(), null, null, null, null, null)));
    }

    @Test
    void reviseNeedsARecordedRevisionRequestWithFeedbackFromTheClient() throws Exception {
        JobSubmission requested = submission(JobSubmissionStatus.REVISION_REQUESTED, contract.getId());
        requested.setReviewerFeedback("please change the logo");
        Map<String, Object> params = service.authorize(clientId, false, contract, "revise",
                new AvalancheEscrowService.Input(null, requested.getId(), null, null, null, null, null));
        assertEquals(sha("avalanche-feedback:" + requested.getId() + ":please change the logo"), params.get("feedbackHash"));
        requested.setReviewerFeedback(" ");
        assertRefused(() -> service.authorize(clientId, false, contract, "revise",
                new AvalancheEscrowService.Input(null, requested.getId(), null, null, null, null, null)));
        requested.setReviewerFeedback("x");
        assertRefused(() -> service.authorize(freelancerId, false, contract, "revise",
                new AvalancheEscrowService.Input(null, requested.getId(), null, null, null, null, null)));
        JobSubmission inReview = submission(JobSubmissionStatus.SUBMITTED, contract.getId());
        assertRefused(() -> service.authorize(clientId, false, contract, "revise",
                new AvalancheEscrowService.Input(null, inReview.getId(), null, null, null, null, null)));
    }

    private ContractDispute dispute(UUID openedBy, DisputeStatus status) {
        ContractDispute d = new ContractDispute();
        d.setId(UUID.randomUUID());
        d.setContractId(contract.getId());
        d.setOpenedBy(openedBy);
        d.setReasonCode("QUALITY");
        d.setStatus(status);
        when(disputes.findById(d.getId())).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void disputeNeedsARecordedDisputeOpenedByTheCaller() throws Exception {
        ContractDispute d = dispute(freelancerId, DisputeStatus.OPEN);
        AvalancheEscrowService.Input input = new AvalancheEscrowService.Input(null, null, d.getId(), null, null, null, null);
        Map<String, Object> params = service.authorize(freelancerId, false, contract, "dispute", input);
        assertEquals(sha("avalanche-dispute:" + d.getId() + ":QUALITY"), params.get("reasonHash"));
        assertEquals("freelancer", params.get("by"));
        assertRefused(() -> service.authorize(clientId, false, contract, "dispute", input));
        assertRefused(() -> service.authorize(strangerId, false, contract, "dispute", input));
        assertRefused(() -> service.authorize(freelancerId, false, contract, "dispute", AvalancheEscrowService.Input.empty()));
    }

    @Test
    void resolveIsAdminOnlyAndFollowsTheRecordedAdministratorDecision() throws Exception {
        ContractDispute release = dispute(clientId, DisputeStatus.DECISION_PENDING_RELEASE);
        ContractDispute refund = dispute(clientId, DisputeStatus.DECISION_PENDING_REFUND);
        ContractDispute open = dispute(clientId, DisputeStatus.OPEN);
        Map<String, Object> toFreelancer = service.authorize(strangerId, true, contract, "resolve",
                new AvalancheEscrowService.Input(null, null, release.getId(), null, null, null, null));
        assertEquals(true, toFreelancer.get("releaseToFreelancer"));
        assertEquals(sha("avalanche-resolution:" + release.getId() + ":RELEASE"), toFreelancer.get("resolutionHash"));
        Map<String, Object> toClient = service.authorize(strangerId, true, contract, "resolve",
                new AvalancheEscrowService.Input(null, null, refund.getId(), null, null, null, null));
        assertEquals(false, toClient.get("releaseToFreelancer"));
        assertRefused(() -> service.authorize(strangerId, true, contract, "resolve", new AvalancheEscrowService.Input(null, null, open.getId(), null, null, null, null)));
        for (UUID notAdmin : List.of(clientId, freelancerId, strangerId)) {
            assertRefused(() -> service.authorize(notAdmin, false, contract, "resolve",
                    new AvalancheEscrowService.Input(null, null, release.getId(), null, null, null, null)));
        }
    }

    @Test
    void remainingActionsRespectTheirRolesAndAnUnknownActionIsRefused() {
        AvalancheEscrowService.Input none = AvalancheEscrowService.Input.empty();
        service.authorize(clientId, false, contract, "extension-approve", none);
        assertRefused(() -> service.authorize(freelancerId, false, contract, "extension-approve", none));
        service.authorize(freelancerId, false, contract, "extension-request", new AvalancheEscrowService.Input(null, null, null, 1_800_000_000L, null, null, null));
        assertRefused(() -> service.authorize(freelancerId, false, contract, "extension-request", none));
        assertRefused(() -> service.authorize(clientId, false, contract, "extension-request",
                new AvalancheEscrowService.Input(null, null, null, 1_800_000_000L, null, null, null)));
        assertEquals("client", service.authorize(clientId, false, contract, "release", none).get("by"));
        assertEquals("relayer", service.authorize(freelancerId, false, contract, "release", none).get("by"));
        assertRefused(() -> service.authorize(strangerId, false, contract, "release", none));
        service.authorize(clientId, false, contract, "mutual-refund", none);
        assertRefused(() -> service.authorize(strangerId, false, contract, "mutual-refund", none));
        assertRefused(() -> service.authorize(clientId, false, contract, "withdraw-everything", none));
    }

    // ---- execution: record first, idempotent, failures are never guessed ----------------------------------------------

    private TransactionResult result(TransactionStatus status) {
        return new TransactionResult(new TransactionReference(new BlockchainNetwork(NETWORK), "key"), status, "0x" + "ab".repeat(32), 7L, null);
    }

    @Test
    void theAttemptIsRecordedBeforeTheGatewayIsCalledAndARepeatedKeyReturnsTheSameRow() {
        when(gateway.executeAction(any())).thenReturn(result(TransactionStatus.SUBMITTED));
        AvalancheEscrowService.Input input = new AvalancheEscrowService.Input("retry-1", null, null, null, null, null, null);
        AvalancheChainTransaction first = service.execute(clientId, false, contract.getId(), "fund", input);
        AvalancheChainTransaction again = service.execute(clientId, false, contract.getId(), "fund", input);

        assertSame(first, again);
        assertEquals(TransactionStatus.SUBMITTED.name(), first.getStatus());
        assertEquals("LOCAL_SIMULATION", first.getEnvironment());
        assertEquals(31337, first.getChainId());
        assertEquals(AvalancheEscrowIds.fromMilestone(milestone.getId()), first.getEscrowId());
        assertEquals(contract.getId() + ":fund:retry-1", first.getOperationKey());
        InOrder order = inOrder(chainTransactions, gateway);
        order.verify(chainTransactions).saveAndFlush(any(AvalancheChainTransaction.class));
        order.verify(gateway).executeAction(any(EscrowActionRequest.class));
        verify(gateway, times(1)).executeAction(any());
    }

    @Test
    void aTimeoutIsUnknownNeverFailedAndADefinitiveRejectionKeepsTheGatewayCode() {
        when(gateway.executeAction(any())).thenThrow(new AvalancheGatewayException("gateway unreachable", false, null, null));
        AvalancheChainTransaction unknown = service.execute(clientId, false, contract.getId(), "fund",
                new AvalancheEscrowService.Input("k-timeout", null, null, null, null, null, null));
        assertEquals(TransactionStatus.UNKNOWN.name(), unknown.getStatus());

        org.mockito.Mockito.doThrow(new AvalancheGatewayException("rejected", true, 409, "SIGNER_UNAVAILABLE")).when(gateway).executeAction(any());
        AvalancheChainTransaction rejected = service.execute(clientId, false, contract.getId(), "fund",
                new AvalancheEscrowService.Input("k-rejected", null, null, null, null, null, null));
        assertEquals(TransactionStatus.FAILED.name(), rejected.getStatus());
        assertEquals("SIGNER_UNAVAILABLE", rejected.getErrorClass());
    }

    @Test
    void anUnauthorizedExecuteNeverReachesTheGatewayOrTheTable() {
        assertRefused(() -> service.execute(strangerId, false, contract.getId(), "fund", null));
        assertRefused(() -> service.execute(freelancerId, false, contract.getId(), "fund", null));
        verify(gateway, never()).executeAction(any());
        verify(chainTransactions, never()).saveAndFlush(any());
    }

    @Test
    void aUserBroadcastTransactionIsRecordedOnceForAnAuthorizedActor() {
        String hash = "0x" + "cd".repeat(32);
        AvalancheChainTransaction row = service.recordUserTransaction(clientId, false, contract.getId(), "fund", hash, null);
        assertEquals(TransactionStatus.SUBMITTED.name(), row.getStatus());
        assertEquals(hash, row.getTxHash());
        assertSame(row, service.recordUserTransaction(clientId, false, contract.getId(), "fund", hash, null));
        assertRefused(() -> service.recordUserTransaction(freelancerId, false, contract.getId(), "fund", hash, null));
        assertRefused(() -> service.recordUserTransaction(clientId, false, contract.getId(), "fund", "0x12", null));
    }

    // ---- business effects only at FINALIZED and only when the chain agrees ---------------------------------------------

    private EscrowSnapshot snapshot(NeutralEscrowStatus status, String amount, String client) {
        return new EscrowSnapshot(new EscrowReference("e"), new AssetReference("t"), status, client, FREELANCER_WALLET, "0xarb", amount, 2, 0, 0,
                0, 0, "0x", "0x");
    }

    private AvalancheChainTransaction row(String action, TransactionStatus status) {
        AvalancheChainTransaction row = new AvalancheChainTransaction();
        row.setContractId(contract.getId());
        row.setMilestoneId(milestone.getId());
        row.setNetworkId(NETWORK);
        row.setEnvironment("LOCAL_SIMULATION");
        row.setChainId(31337);
        row.setEscrowId(AvalancheEscrowIds.fromMilestone(milestone.getId()));
        row.setAction(action);
        row.setOperationKey(contract.getId() + ":" + action + ":x");
        row.setActorUserId(clientId);
        row.setStatus(status.name());
        rows.add(row);
        return row;
    }

    @Test
    void fundingIsAppliedOnlyWhenFinalizedAndTheChainMatchesTheRecordedTerms() {
        AvalancheChainTransaction confirmedOnly = row("fund", TransactionStatus.SUBMITTED);
        when(gateway.transactionStatus(any())).thenReturn(result(TransactionStatus.CONFIRMED));
        service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.PENDING_FUNDING, milestone.getStatus(), "CONFIRMED is not settlement");
        assertFalse(confirmedOnly.isBusinessApplied());

        when(gateway.transactionStatus(any())).thenReturn(result(TransactionStatus.FINALIZED));
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.FUNDED, "4999999", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.PENDING_FUNDING, milestone.getStatus(), "an amount that differs from the record is not accepted");
        assertEquals("CHAIN_MISMATCH", confirmedOnly.getErrorClass());
        assertFalse(confirmedOnly.isBusinessApplied());

        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.FUNDED, "5000000", "0x" + "99".repeat(20)));
        service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.PENDING_FUNDING, milestone.getStatus(), "another client wallet is not accepted");

        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.FUNDED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.FUNDED, milestone.getStatus());
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertTrue(confirmedOnly.isBusinessApplied());
        assertNull(confirmedOnly.getErrorClass());

        milestone.setStatus(MilestoneStatus.IN_PROGRESS);
        service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.IN_PROGRESS, milestone.getStatus(), "an applied effect is never applied twice");
    }

    @Test
    void releaseAndRefundOutcomesFollowTheChainNotTheRequest() {
        contract.setStatus(ContractStatus.ACTIVE);
        milestone.setStatus(MilestoneStatus.SUBMITTED);
        row("release", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.SUBMITTED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.SUBMITTED, milestone.getStatus(), "FINALIZED release that the chain does not show is not applied");

        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.RELEASED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.RELEASED, milestone.getStatus());
        assertEquals(ContractStatus.COMPLETED, contract.getStatus());

        rows.clear();
        contract.setStatus(ContractStatus.DISPUTED);
        milestone.setStatus(MilestoneStatus.DISPUTED);
        row("mutual-refund", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.REFUNDED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.REFUNDED, milestone.getStatus());
        assertEquals(ContractStatus.CANCELLED, contract.getStatus());
    }

    @Test
    void anUnreachableGatewayDuringRefreshLeavesTheStateAlone() {
        AvalancheChainTransaction pending = row("fund", TransactionStatus.SUBMITTED);
        when(gateway.transactionStatus(any())).thenThrow(new AvalancheGatewayException("down", false, null, null));
        service.refresh(clientId, false, contract.getId());
        assertEquals(TransactionStatus.SUBMITTED.name(), pending.getStatus());
        assertEquals("GATEWAY_UNREACHABLE", pending.getErrorClass());
        assertEquals(MilestoneStatus.PENDING_FUNDING, milestone.getStatus());
    }

    /**
     * Register item A3 (marketplace half): the gateway faults seen through the marketplace, in ONE sequence on one contract:
     * timeout at execute, impatient duplicate, HTTP 429 and outage during refresh, UNKNOWN and non-final states, a FINALIZED
     * status whose chain read then fails, a chain read that disagrees, recovery, and duplicate FINALIZED observations.
     * Invariants: one gateway execute per business operation; no business effect before FINALIZED plus an agreeing chain read;
     * the effect is applied exactly once; no fault turns into FAILED. The gateway half is CombinedFaultScenarioTest.
     */
    @Test
    void combinedGatewayFaultsNeverApplyEarlyNeverFailAndApplyExactlyOnce() {
        AvalancheEscrowService.Input input = new AvalancheEscrowService.Input("a3-fund", null, null, null, null, null, null);

        // 1. timeout at execute: recorded first, then UNKNOWN (never FAILED)
        when(gateway.executeAction(any())).thenThrow(new AvalancheGatewayException("timeout", false, null, null));
        AvalancheChainTransaction row = service.execute(clientId, false, contract.getId(), "fund", input);
        assertEquals(TransactionStatus.UNKNOWN.name(), row.getStatus());
        assertEquals("GATEWAY_UNREACHABLE", row.getErrorClass());

        // 2. impatient duplicate with the same idempotency key: same row, no second execute
        assertSame(row, service.execute(clientId, false, contract.getId(), "fund", input));
        verify(gateway, times(1)).executeAction(any());

        // 3. HTTP 429 during refresh: state kept, rate-limit code recorded
        when(gateway.transactionStatus(any())).thenThrow(new AvalancheGatewayException("throttled", false, 429, "RATE_LIMITED"));
        service.refresh(clientId, false, contract.getId());
        assertEquals(TransactionStatus.UNKNOWN.name(), row.getStatus());
        assertEquals("RATE_LIMITED", row.getErrorClass());

        // 4. gateway still unsure, then mined but not final: no business effect
        org.mockito.Mockito.doReturn(result(TransactionStatus.UNKNOWN)).when(gateway).transactionStatus(any());
        service.refresh(clientId, false, contract.getId());
        org.mockito.Mockito.doReturn(result(TransactionStatus.CONFIRMED)).when(gateway).transactionStatus(any());
        service.refresh(clientId, false, contract.getId());
        assertEquals(TransactionStatus.CONFIRMED.name(), row.getStatus());
        assertEquals(MilestoneStatus.PENDING_FUNDING, milestone.getStatus(), "CONFIRMED is not settlement");

        // 5. FINALIZED, but the independent chain read fails: the refresh fails as a whole (rolled back in a transaction),
        //    nothing is applied
        org.mockito.Mockito.doReturn(result(TransactionStatus.FINALIZED)).when(gateway).transactionStatus(any());
        when(gateway.escrowSnapshot(any())).thenThrow(new AvalancheGatewayException("down", false, null, null));
        assertThrows(AvalancheGatewayException.class, () -> service.refresh(clientId, false, contract.getId()));
        assertFalse(row.isBusinessApplied());
        assertEquals(MilestoneStatus.PENDING_FUNDING, milestone.getStatus());

        // 6. the chain read disagrees with the recorded terms (missed or foreign event): CHAIN_MISMATCH, nothing applied
        org.mockito.Mockito.doReturn(snapshot(NeutralEscrowStatus.NONE, "0", CLIENT_WALLET)).when(gateway).escrowSnapshot(any());
        service.refresh(clientId, false, contract.getId());
        assertEquals("CHAIN_MISMATCH", row.getErrorClass());
        assertFalse(row.isBusinessApplied());
        assertEquals(MilestoneStatus.PENDING_FUNDING, milestone.getStatus());

        // 7. recovery: the chain agrees; a new service instance (restart) applies the effect once
        org.mockito.Mockito.doReturn(snapshot(NeutralEscrowStatus.FUNDED, "5000000", CLIENT_WALLET)).when(gateway).escrowSnapshot(any());
        AvalancheEscrowService restarted = new AvalancheEscrowService(contracts, milestones, wallets, submissions, disputes, solanaEscrows,
                chainTransactions, new BlockchainGatewayRegistry(List.of(gateway)), properties, evmWallets, pendingAuthorizations);
        restarted.refresh(clientId, false, contract.getId());
        assertTrue(row.isBusinessApplied());
        assertNull(row.getErrorClass());
        assertEquals(MilestoneStatus.FUNDED, milestone.getStatus());
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());

        // 8. duplicate FINALIZED observations never apply twice; still exactly one execute
        milestone.setStatus(MilestoneStatus.IN_PROGRESS);
        restarted.refresh(clientId, false, contract.getId());
        service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.IN_PROGRESS, milestone.getStatus());
        assertEquals(1, rows.size(), "one row per business operation");
        verify(gateway, times(1)).executeAction(any());
        assertFalse(rows.stream().anyMatch(r -> TransactionStatus.FAILED.name().equals(r.getStatus())));
    }

    // ---- A8: a proven EVM wallet is used for the escrow parties -------------------------------------------------------

    @Test
    void aWalletLinkedByChallengeTakesPrecedenceOverTheLegacyWalletRow() {
        com.marketplace.backend.entity.EvmWallet linked = new com.marketplace.backend.entity.EvmWallet();
        linked.setUserId(freelancerId);
        linked.setAddress("0x" + "7e".repeat(20));
        when(evmWallets.findByUserId(freelancerId)).thenReturn(Optional.of(linked));
        Map<String, Object> p = service.authorize(clientId, false, contract, "fund", AvalancheEscrowService.Input.empty());
        assertEquals("0x" + "7e".repeat(20), p.get("freelancer"));
        assertEquals(CLIENT_WALLET.toLowerCase(), p.get("client"), "without a linked EVM wallet the existing behaviour is kept");
    }

    // ---- F13: two parties sign the same mutual-refund typed data with their own wallets ----------------------------

    private static final String DIGEST = "0x" + "d1".repeat(32);
    private static final String CLIENT_SIG = "0x" + "c1".repeat(65);
    private static final String FREELANCER_SIG = "0x" + "f1".repeat(65);

    private void gatewayExpects(String digest) {
        when(gateway.prepareAction(any())).thenReturn(new com.marketplace.backend.blockchain.PreparedAction("mutual-refund", "0x1",
                null, List.of(), 0L, "{}", digest));
    }

    @Test
    void eachPartyStoresOnlyItsOwnSignatureOverTheDigestTheGatewayExpects() {
        contract.setStatus(ContractStatus.ACTIVE);
        long deadline = Instant.now().getEpochSecond() + 300;
        gatewayExpects(DIGEST);
        when(gateway.escrowSnapshot(any())).thenReturn(null);

        AvalancheEscrowService.ContractView afterClient = service.submitMutualRefundSignature(clientId, contract.getId(), deadline, DIGEST, CLIENT_SIG);
        assertTrue(afterClient.mutualRefund().clientSigned());
        assertFalse(afterClient.mutualRefund().freelancerSigned());
        AvalancheEscrowService.ContractView afterBoth = service.submitMutualRefundSignature(freelancerId, contract.getId(), deadline, DIGEST, FREELANCER_SIG);
        assertTrue(afterBoth.mutualRefund().clientSigned() && afterBoth.mutualRefund().freelancerSigned());
        assertEquals(1, pendingRows.size());
        assertEquals(CLIENT_SIG, pendingRows.get(0).getClientSignature(), "the freelancer did not overwrite the client's signature");

        org.mockito.ArgumentCaptor<EscrowActionRequest> sent = org.mockito.ArgumentCaptor.forClass(EscrowActionRequest.class);
        verify(gateway, times(2)).prepareAction(sent.capture());
        assertEquals(deadline, sent.getValue().parameters().get("authorizationDeadline"), "the gateway re-derives the digest for this deadline");
    }

    @Test
    void signaturesAreRefusedForStrangersExpiredDeadlinesMalformedInputAndAForeignDigest() {
        long deadline = Instant.now().getEpochSecond() + 300;
        gatewayExpects(DIGEST);
        assertRefused(() -> service.submitMutualRefundSignature(strangerId, contract.getId(), deadline, DIGEST, CLIENT_SIG));
        assertRefused(() -> service.submitMutualRefundSignature(clientId, contract.getId(), Instant.now().getEpochSecond() - 1, DIGEST, CLIENT_SIG));
        assertRefused(() -> service.submitMutualRefundSignature(clientId, contract.getId(), null, DIGEST, CLIENT_SIG));
        assertRefused(() -> service.submitMutualRefundSignature(clientId, contract.getId(), deadline, "0x12", CLIENT_SIG));
        assertRefused(() -> service.submitMutualRefundSignature(clientId, contract.getId(), deadline, DIGEST, "0x12"));
        assertRefused(() -> service.submitMutualRefundSignature(clientId, contract.getId(), deadline, "0x" + "ee".repeat(32), CLIENT_SIG));
        assertTrue(pendingRows.isEmpty());
        contract.setPaymentNetworkId(null);
        assertRefused(() -> service.submitMutualRefundSignature(clientId, contract.getId(), deadline, DIGEST, CLIENT_SIG));
    }

    @Test
    void aNewDeadlineStartsANewPairSoStaleSignaturesAreNeverCombined() {
        long first = Instant.now().getEpochSecond() + 300;
        long second = first + 60;
        gatewayExpects(DIGEST);
        when(gateway.escrowSnapshot(any())).thenReturn(null);
        service.submitMutualRefundSignature(clientId, contract.getId(), first, DIGEST, CLIENT_SIG);
        String otherDigest = "0x" + "d2".repeat(32);
        gatewayExpects(otherDigest);
        AvalancheEscrowService.ContractView v = service.submitMutualRefundSignature(freelancerId, contract.getId(), second, otherDigest, FREELANCER_SIG);
        assertFalse(v.mutualRefund().clientSigned(), "the client signed a different deadline and must sign again");
        assertTrue(v.mutualRefund().freelancerSigned());
        assertEquals(second, v.mutualRefund().authorizationDeadline());
    }

    @Test
    void theHeldPairAndItsDeadlineAreSentOnlyWhenBothSignedAndNotExpired() {
        com.marketplace.backend.entity.AvalanchePendingAuthorization a = new com.marketplace.backend.entity.AvalanchePendingAuthorization();
        a.setContractId(contract.getId());
        a.setAction("mutual-refund");
        a.setAuthorizationDeadline(Instant.now().getEpochSecond() + 300);
        a.setTypedDataDigest(DIGEST);
        a.setClientSignature(CLIENT_SIG);
        pendingRows.add(a);
        Map<String, Object> half = service.authorize(clientId, false, contract, "mutual-refund", AvalancheEscrowService.Input.empty());
        assertFalse(half.containsKey("clientSignature"), "one signature is never sent");

        a.setFreelancerSignature(FREELANCER_SIG);
        Map<String, Object> both = service.authorize(freelancerId, false, contract, "mutual-refund", AvalancheEscrowService.Input.empty());
        assertEquals(CLIENT_SIG, both.get("clientSignature"));
        assertEquals(FREELANCER_SIG, both.get("freelancerSignature"));
        assertEquals(a.getAuthorizationDeadline(), both.get("authorizationDeadline"));

        a.setAuthorizationDeadline(Instant.now().getEpochSecond() - 1);
        assertTrue(service.authorize(clientId, false, contract, "mutual-refund", AvalancheEscrowService.Input.empty()).isEmpty(),
                "an expired pair is not used");
        assertRefused(() -> service.authorize(strangerId, false, contract, "mutual-refund", AvalancheEscrowService.Input.empty()));

        Map<String, Object> second = service.authorize(freelancerId, false, contract, "mutual-refund",
                new AvalancheEscrowService.Input(null, null, null, null, null, null, null, 1_900_000_000L));
        assertEquals(1_900_000_000L, second.get("authorizationDeadline"), "the second signer prepares over the first deadline");
    }

    @Test
    void aFinalizedMutualRefundSpendsTheHeldSignatures() {
        com.marketplace.backend.entity.AvalanchePendingAuthorization a = new com.marketplace.backend.entity.AvalanchePendingAuthorization();
        a.setContractId(contract.getId());
        a.setAction("mutual-refund");
        a.setAuthorizationDeadline(Instant.now().getEpochSecond() + 300);
        a.setTypedDataDigest(DIGEST);
        pendingRows.add(a);
        contract.setStatus(ContractStatus.ACTIVE);
        milestone.setStatus(MilestoneStatus.IN_PROGRESS);
        row("mutual-refund", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.REFUNDED, "5000000", CLIENT_WALLET));
        AvalancheEscrowService.ContractView v = service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.REFUNDED, milestone.getStatus());
        assertTrue(pendingRows.isEmpty());
        assertNull(v.mutualRefund());
    }

    @Test
    void onlyParticipantsAndAdministratorsSeeTheStatus() {
        assertRefused(() -> service.status(strangerId, false, contract.getId()));
        when(gateway.escrowSnapshot(any())).thenReturn(null);
        assertEquals(NETWORK, service.status(clientId, false, contract.getId()).networkId());
        assertTrue(service.status(strangerId, true, contract.getId()).avalancheEnabled());
        contract.setPaymentNetworkId(null);
        assertFalse(service.status(clientId, false, contract.getId()).avalancheEnabled());
    }
}
