package com.marketplace.backend.service.impl;

import com.marketplace.backend.blockchain.AssetReference;
import com.marketplace.backend.blockchain.BlockchainAddress;
import com.marketplace.backend.blockchain.BlockchainGateway;
import com.marketplace.backend.blockchain.BlockchainGatewayRegistry;
import com.marketplace.backend.blockchain.BlockchainNetwork;
import com.marketplace.backend.blockchain.EnvironmentInfo;
import com.marketplace.backend.blockchain.EscrowActionRequest;
import com.marketplace.backend.blockchain.EscrowReference;
import com.marketplace.backend.blockchain.EscrowSnapshot;
import com.marketplace.backend.blockchain.IssuerAlert;
import com.marketplace.backend.blockchain.NeutralEscrowStatus;
import com.marketplace.backend.blockchain.PreparedAction;
import com.marketplace.backend.blockchain.TransactionReference;
import com.marketplace.backend.blockchain.TransactionResult;
import com.marketplace.backend.blockchain.TransactionStatus;
import com.marketplace.backend.blockchain.avalanche.AvalancheEscrowIds;
import com.marketplace.backend.configuration.AvalancheGatewayProperties;
import com.marketplace.backend.dto.request.avalanche.AvalancheActionInput;
import com.marketplace.backend.dto.response.avalanche.AvalancheContractView;
import com.marketplace.backend.entity.AvalancheChainTransaction;
import com.marketplace.backend.entity.AvalanchePendingAuthorization;
import com.marketplace.backend.entity.ContractDispute;
import com.marketplace.backend.entity.ContractStatus;
import com.marketplace.backend.entity.DisputeStatus;
import com.marketplace.backend.entity.EscrowContract;
import com.marketplace.backend.entity.EvmWallet;
import com.marketplace.backend.entity.Job;
import com.marketplace.backend.entity.JobStatus;
import com.marketplace.backend.entity.JobSubmission;
import com.marketplace.backend.entity.JobSubmissionStatus;
import com.marketplace.backend.entity.Milestone;
import com.marketplace.backend.entity.MilestoneStatus;
import com.marketplace.backend.entity.NotificationType;
import com.marketplace.backend.entity.Wallet;
import com.marketplace.backend.entity.WorkContract;
import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.AvalancheGatewayException;
import com.marketplace.backend.exception.ErrorCode;
import com.marketplace.backend.repository.AvalancheChainTransactionRepository;
import com.marketplace.backend.repository.AvalanchePendingAuthorizationRepository;
import com.marketplace.backend.repository.ContractDisputeRepository;
import com.marketplace.backend.repository.EscrowContractRepository;
import com.marketplace.backend.repository.EvmWalletRepository;
import com.marketplace.backend.repository.JobRepository;
import com.marketplace.backend.repository.JobSubmissionRepository;
import com.marketplace.backend.repository.MilestoneRepository;
import com.marketplace.backend.repository.WalletRepository;
import com.marketplace.backend.repository.WorkContractRepository;
import com.marketplace.backend.service.AvalancheEscrowService;
import com.marketplace.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

class AvalancheEscrowServiceImplTest {
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
    private final EvmWalletRepository evmWallets = mock(EvmWalletRepository.class);
    private final AvalanchePendingAuthorizationRepository pendingAuthorizations =
            mock(AvalanchePendingAuthorizationRepository.class);
    private final List<AvalanchePendingAuthorization> pendingRows = new ArrayList<>();
    private final NotificationService notifications = mock(NotificationService.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final AvalancheGatewayProperties properties = new AvalancheGatewayProperties();
    private AvalancheEscrowService service;
    private AvalancheActionAuthorizer authorizer;

    private final UUID clientId = UUID.randomUUID();
    private final UUID freelancerId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();
    private WorkContract contract;
    private Milestone milestone;
    private final List<AvalancheChainTransaction> rows = new ArrayList<>();

    private AvalancheEscrowService newService() {
        return new AvalancheEscrowServiceImpl(contracts, milestones, solanaEscrows, chainTransactions, disputes,
                new BlockchainGatewayRegistry(List.of(gateway)), properties, pendingAuthorizations, notifications, jobs, authorizer);
    }

    @BeforeEach
    void setUp() {
        properties.setNetwork(NETWORK);
        when(gateway.network()).thenReturn(new BlockchainNetwork(NETWORK));
        when(gateway.environmentInfo()).thenReturn(new EnvironmentInfo("LOCAL_SIMULATION", 31337, true, null, true));
        when(gateway.validateAddress(any(BlockchainAddress.class))).thenReturn(true);
        authorizer = new AvalancheActionAuthorizer(milestones, wallets, submissions, disputes, new BlockchainGatewayRegistry(List.of(gateway)),
                properties, evmWallets, pendingAuthorizations);
        service = newService();

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
        Answer<Optional<AvalanchePendingAuthorization>> findPending = invocation ->
                pendingRows.stream().filter(a -> a.getContractId().equals(invocation.getArgument(0))
                        && a.getAction().equals(invocation.getArgument(1))).findFirst();
        when(pendingAuthorizations.findByContractIdAndAction(any(), anyString())).thenAnswer(findPending);
        when(pendingAuthorizations.findWithLockByContractIdAndAction(any(), anyString())).thenAnswer(findPending);
        when(pendingAuthorizations.saveAndFlush(any(AvalanchePendingAuthorization.class))).thenAnswer(invocation -> {
            AvalanchePendingAuthorization a = invocation.getArgument(0);
            if (!pendingRows.contains(a)) {
                pendingRows.add(a);
            }
            return a;
        });
        Mockito.doAnswer(invocation -> pendingRows.remove(invocation.<Object>getArgument(0)))
                .when(pendingAuthorizations).delete(any(AvalanchePendingAuthorization.class));
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
        return "0x" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
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
        Map<String, Object> params = authorizer.authorize(clientId, false, contract, "fund", AvalancheActionInput.empty());
        assertEquals(CLIENT_WALLET, params.get("client"));
        assertEquals(FREELANCER_WALLET, params.get("freelancer"));
        assertEquals("5000000", params.get("amount"));
        assertEquals(contract.getDeliveryDueAt().getEpochSecond(), params.get("deliveryDueAt"));
        assertEquals(72, params.get("reviewWindowHours"));
        assertEquals(2, params.get("maxRevisions"));
        assertEquals(false, params.get("highValueGrace"));
        long fundingExpiry = (Long) params.get("fundingExpiresAt");
        assertTrue(fundingExpiry > Instant.now().plusSeconds(47 * 3600).getEpochSecond() && fundingExpiry < Instant.now().plusSeconds(49 * 3600).getEpochSecond());
        List<String> inputFields = Arrays.stream(AvalancheActionInput.class.getRecordComponents()).map(c -> c.getName()).toList();
        for (String forbidden : List.of("amount", "client", "freelancer", "deliveryDueAt", "reviewWindowHours", "maxRevisions", "arbiter", "chainId", "rpcUrl", "privateKey")) {
            assertFalse(inputFields.contains(forbidden), "the caller cannot supply " + forbidden);
        }
    }

    @Test
    void fundIsRefusedForTheWrongRoleStateWalletsAmountOrReviewWindow() {
        AvalancheActionInput none = AvalancheActionInput.empty();
        assertRefused(() -> authorizer.authorize(freelancerId, false, contract, "fund", none));
        assertRefused(() -> authorizer.authorize(strangerId, false, contract, "fund", none));
        assertRefused(() -> authorizer.authorize(strangerId, true, contract, "fund", none));
        contract.setStatus(ContractStatus.ACTIVE);
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "fund", none));
        contract.setStatus(ContractStatus.PENDING_FUNDING);
        milestone.setAmount(new BigDecimal("6.00"));
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "fund", none));
        milestone.setAmount(new BigDecimal("5.00"));
        contract.setReviewWindowHours(24);
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "fund", none));
        contract.setReviewWindowHours(72);
        when(wallets.findFirstByUserIdOrderByIdAsc(freelancerId)).thenReturn(Optional.empty());
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "fund", none));
        assertEquals(ErrorCode.AVALANCHE_WALLET_NOT_LINKED, assertThrows(ApplicationException.class,
                () -> authorizer.authorize(clientId, false, contract, "fund", none)).getErrorCode(), "the client is told what is missing");
        when(wallets.findFirstByUserIdOrderByIdAsc(freelancerId)).thenReturn(Optional.of(wallet(FREELANCER_WALLET)));
        when(gateway.validateAddress(new BlockchainAddress(FREELANCER_WALLET))).thenReturn(false);
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "fund", none));
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
        Map<String, Object> params = authorizer.authorize(freelancerId, false, contract, "submit",
                new AvalancheActionInput(null, recorded.getId(), null, null, null, null, null));
        assertEquals(sha("avalanche-evidence:" + contract.getId() + ":" + recorded.getId() + ":payload-hash"), params.get("evidenceHash"));

        JobSubmission foreign = submission(JobSubmissionStatus.SUBMITTED, UUID.randomUUID());
        assertRefused(() -> authorizer.authorize(freelancerId, false, contract, "submit",
                new AvalancheActionInput(null, foreign.getId(), null, null, null, null, null)));
        JobSubmission approved = submission(JobSubmissionStatus.APPROVED, contract.getId());
        assertRefused(() -> authorizer.authorize(freelancerId, false, contract, "submit",
                new AvalancheActionInput(null, approved.getId(), null, null, null, null, null)));
        assertRefused(() -> authorizer.authorize(freelancerId, false, contract, "submit", AvalancheActionInput.empty()));
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "submit",
                new AvalancheActionInput(null, recorded.getId(), null, null, null, null, null)));
    }

    @Test
    void reviseNeedsARecordedRevisionRequestWithFeedbackFromTheClient() throws Exception {
        JobSubmission requested = submission(JobSubmissionStatus.REVISION_REQUESTED, contract.getId());
        requested.setReviewerFeedback("please change the logo");
        Map<String, Object> params = authorizer.authorize(clientId, false, contract, "revise",
                new AvalancheActionInput(null, requested.getId(), null, null, null, null, null));
        assertEquals(sha("avalanche-feedback:" + requested.getId() + ":please change the logo"), params.get("feedbackHash"));
        requested.setReviewerFeedback(" ");
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "revise",
                new AvalancheActionInput(null, requested.getId(), null, null, null, null, null)));
        requested.setReviewerFeedback("x");
        assertRefused(() -> authorizer.authorize(freelancerId, false, contract, "revise",
                new AvalancheActionInput(null, requested.getId(), null, null, null, null, null)));
        JobSubmission inReview = submission(JobSubmissionStatus.SUBMITTED, contract.getId());
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "revise",
                new AvalancheActionInput(null, inReview.getId(), null, null, null, null, null)));
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
        AvalancheActionInput input = new AvalancheActionInput(null, null, d.getId(), null, null, null, null);
        Map<String, Object> params = authorizer.authorize(freelancerId, false, contract, "dispute", input);
        assertEquals(sha("avalanche-dispute:" + d.getId() + ":QUALITY"), params.get("reasonHash"));
        assertEquals("freelancer", params.get("by"));
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "dispute", input));
        assertRefused(() -> authorizer.authorize(strangerId, false, contract, "dispute", input));
        assertRefused(() -> authorizer.authorize(freelancerId, false, contract, "dispute", AvalancheActionInput.empty()));
    }

    @Test
    void resolveIsAdminOnlyAndFollowsTheRecordedAdministratorDecision() throws Exception {
        ContractDispute release = dispute(clientId, DisputeStatus.DECISION_PENDING_RELEASE);
        ContractDispute refund = dispute(clientId, DisputeStatus.DECISION_PENDING_REFUND);
        ContractDispute open = dispute(clientId, DisputeStatus.OPEN);
        Map<String, Object> toFreelancer = authorizer.authorize(strangerId, true, contract, "resolve",
                new AvalancheActionInput(null, null, release.getId(), null, null, null, null));
        assertEquals(true, toFreelancer.get("releaseToFreelancer"));
        assertEquals(sha("avalanche-resolution:" + release.getId() + ":RELEASE"), toFreelancer.get("resolutionHash"));
        Map<String, Object> toClient = authorizer.authorize(strangerId, true, contract, "resolve",
                new AvalancheActionInput(null, null, refund.getId(), null, null, null, null));
        assertEquals(false, toClient.get("releaseToFreelancer"));
        assertRefused(() -> authorizer.authorize(strangerId, true, contract, "resolve", new AvalancheActionInput(null, null, open.getId(), null, null, null, null)));
        for (UUID notAdmin : List.of(clientId, freelancerId, strangerId)) {
            assertRefused(() -> authorizer.authorize(notAdmin, false, contract, "resolve",
                    new AvalancheActionInput(null, null, release.getId(), null, null, null, null)));
        }
        // a party holding the administrator role cannot arbitrate its own contract
        for (UUID party : List.of(clientId, freelancerId)) {
            assertRefused(() -> authorizer.authorize(party, true, contract, "resolve",
                    new AvalancheActionInput(null, null, release.getId(), null, null, null, null)));
        }
    }

    @Test
    void remainingActionsRespectTheirRolesAndAnUnknownActionIsRefused() {
        AvalancheActionInput none = AvalancheActionInput.empty();
        authorizer.authorize(clientId, false, contract, "extension-approve", none);
        assertRefused(() -> authorizer.authorize(freelancerId, false, contract, "extension-approve", none));
        authorizer.authorize(freelancerId, false, contract, "extension-request", new AvalancheActionInput(null, null, null, 1_800_000_000L, null, null, null));
        assertRefused(() -> authorizer.authorize(freelancerId, false, contract, "extension-request", none));
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "extension-request",
                new AvalancheActionInput(null, null, null, 1_800_000_000L, null, null, null)));
        assertEquals("client", authorizer.authorize(clientId, false, contract, "release", none).get("by"));
        assertEquals("relayer", authorizer.authorize(freelancerId, false, contract, "release", none).get("by"));
        assertRefused(() -> authorizer.authorize(strangerId, false, contract, "release", none));
        authorizer.authorize(clientId, false, contract, "mutual-refund", none);
        assertRefused(() -> authorizer.authorize(strangerId, false, contract, "mutual-refund", none));
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "withdraw-everything", none));
    }

    // ---- execution: record first, idempotent, failures are never guessed ----------------------------------------------

    private TransactionResult result(TransactionStatus status) {
        return new TransactionResult(new TransactionReference(new BlockchainNetwork(NETWORK), "key"), status, "0x" + "ab".repeat(32), 7L, null);
    }

    @Test
    void theAttemptIsRecordedBeforeTheGatewayIsCalledAndARepeatedKeyReturnsTheSameRow() {
        when(gateway.executeAction(any())).thenReturn(result(TransactionStatus.SUBMITTED));
        AvalancheActionInput input = new AvalancheActionInput("retry-1", null, null, null, null, null, null);
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
                new AvalancheActionInput("k-timeout", null, null, null, null, null, null));
        assertEquals(TransactionStatus.UNKNOWN.name(), unknown.getStatus());

        Mockito.doThrow(new AvalancheGatewayException("rejected", true, 409, "SIGNER_UNAVAILABLE")).when(gateway).executeAction(any());
        AvalancheChainTransaction rejected = service.execute(clientId, false, contract.getId(), "fund",
                new AvalancheActionInput("k-rejected", null, null, null, null, null, null));
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
        AvalancheActionInput input = new AvalancheActionInput("a3-fund", null, null, null, null, null, null);

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
        Mockito.doReturn(result(TransactionStatus.UNKNOWN)).when(gateway).transactionStatus(any());
        service.refresh(clientId, false, contract.getId());
        Mockito.doReturn(result(TransactionStatus.CONFIRMED)).when(gateway).transactionStatus(any());
        service.refresh(clientId, false, contract.getId());
        assertEquals(TransactionStatus.CONFIRMED.name(), row.getStatus());
        assertEquals(MilestoneStatus.PENDING_FUNDING, milestone.getStatus(), "CONFIRMED is not settlement");

        // 5. FINALIZED, but the independent chain read fails: the refresh fails as a whole (rolled back in a transaction),
        //    nothing is applied
        Mockito.doReturn(result(TransactionStatus.FINALIZED)).when(gateway).transactionStatus(any());
        when(gateway.escrowSnapshot(any())).thenThrow(new AvalancheGatewayException("down", false, null, null));
        assertThrows(AvalancheGatewayException.class, () -> service.refresh(clientId, false, contract.getId()));
        assertFalse(row.isBusinessApplied());
        assertEquals(MilestoneStatus.PENDING_FUNDING, milestone.getStatus());

        // 6. the chain read disagrees with the recorded terms (missed or foreign event): CHAIN_MISMATCH, nothing applied
        Mockito.doReturn(snapshot(NeutralEscrowStatus.NONE, "0", CLIENT_WALLET)).when(gateway).escrowSnapshot(any());
        service.refresh(clientId, false, contract.getId());
        assertEquals("CHAIN_MISMATCH", row.getErrorClass());
        assertFalse(row.isBusinessApplied());
        assertEquals(MilestoneStatus.PENDING_FUNDING, milestone.getStatus());

        // 7. recovery: the chain agrees; a new service instance (restart) applies the effect once
        Mockito.doReturn(snapshot(NeutralEscrowStatus.FUNDED, "5000000", CLIENT_WALLET)).when(gateway).escrowSnapshot(any());
        AvalancheEscrowService restarted = newService();
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
        EvmWallet linked = new EvmWallet();
        linked.setUserId(freelancerId);
        linked.setAddress("0x" + "7e".repeat(20));
        when(evmWallets.findByUserId(freelancerId)).thenReturn(Optional.of(linked));
        Map<String, Object> p = authorizer.authorize(clientId, false, contract, "fund", AvalancheActionInput.empty());
        assertEquals("0x" + "7e".repeat(20), p.get("freelancer"));
        assertEquals(CLIENT_WALLET.toLowerCase(), p.get("client"), "without a linked EVM wallet the existing behaviour is kept");
    }

    // ---- F13: two parties sign the same mutual-refund typed data with their own wallets ----------------------------

    private static final String DIGEST = "0x" + "d1".repeat(32);
    private static final String CLIENT_SIG = "0x" + "c1".repeat(65);
    private static final String FREELANCER_SIG = "0x" + "f1".repeat(65);

    private void gatewayExpects(String digest) {
        when(gateway.prepareAction(any())).thenReturn(new PreparedAction("mutual-refund", "0x1",
                null, List.of(), 0L, "{}", digest));
    }

    @Test
    void eachPartyStoresOnlyItsOwnSignatureOverTheDigestTheGatewayExpects() {
        contract.setStatus(ContractStatus.ACTIVE);
        long deadline = Instant.now().getEpochSecond() + 300;
        gatewayExpects(DIGEST);
        when(gateway.escrowSnapshot(any())).thenReturn(null);

        AvalancheContractView afterClient = service.submitMutualRefundSignature(clientId, contract.getId(), deadline, DIGEST, CLIENT_SIG);
        assertTrue(afterClient.mutualRefund().clientSigned());
        assertFalse(afterClient.mutualRefund().freelancerSigned());
        AvalancheContractView afterBoth = service.submitMutualRefundSignature(freelancerId, contract.getId(), deadline, DIGEST, FREELANCER_SIG);
        assertTrue(afterBoth.mutualRefund().clientSigned() && afterBoth.mutualRefund().freelancerSigned());
        assertEquals(1, pendingRows.size());
        assertEquals(CLIENT_SIG, pendingRows.get(0).getClientSignature(), "the freelancer did not overwrite the client's signature");

        ArgumentCaptor<EscrowActionRequest> sent = ArgumentCaptor.forClass(EscrowActionRequest.class);
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
        AvalancheContractView v = service.submitMutualRefundSignature(freelancerId, contract.getId(), second, otherDigest, FREELANCER_SIG);
        assertFalse(v.mutualRefund().clientSigned(), "the client signed a different deadline and must sign again");
        assertTrue(v.mutualRefund().freelancerSigned());
        assertEquals(second, v.mutualRefund().authorizationDeadline());
    }

    @Test
    void theHeldPairAndItsDeadlineAreSentOnlyWhenBothSignedAndNotExpired() {
        AvalanchePendingAuthorization a = new AvalanchePendingAuthorization();
        a.setContractId(contract.getId());
        a.setAction("mutual-refund");
        a.setAuthorizationDeadline(Instant.now().getEpochSecond() + 300);
        a.setTypedDataDigest(DIGEST);
        a.setClientSignature(CLIENT_SIG);
        pendingRows.add(a);
        Map<String, Object> half = authorizer.authorize(clientId, false, contract, "mutual-refund", AvalancheActionInput.empty());
        assertFalse(half.containsKey("clientSignature"), "one signature is never sent");

        a.setFreelancerSignature(FREELANCER_SIG);
        Map<String, Object> both = authorizer.authorize(freelancerId, false, contract, "mutual-refund", AvalancheActionInput.empty());
        assertEquals(CLIENT_SIG, both.get("clientSignature"));
        assertEquals(FREELANCER_SIG, both.get("freelancerSignature"));
        assertEquals(a.getAuthorizationDeadline(), both.get("authorizationDeadline"));

        a.setAuthorizationDeadline(Instant.now().getEpochSecond() - 1);
        assertTrue(authorizer.authorize(clientId, false, contract, "mutual-refund", AvalancheActionInput.empty()).isEmpty(),
                "an expired pair is not used");
        assertRefused(() -> authorizer.authorize(strangerId, false, contract, "mutual-refund", AvalancheActionInput.empty()));

        Map<String, Object> second = authorizer.authorize(freelancerId, false, contract, "mutual-refund",
                new AvalancheActionInput(null, null, null, null, null, null, null, 1_900_000_000L));
        assertEquals(1_900_000_000L, second.get("authorizationDeadline"), "the second signer prepares over the first deadline");
    }

    @Test
    void aFinalizedMutualRefundSpendsTheHeldSignatures() {
        AvalanchePendingAuthorization a = new AvalanchePendingAuthorization();
        a.setContractId(contract.getId());
        a.setAction("mutual-refund");
        a.setAuthorizationDeadline(Instant.now().getEpochSecond() + 300);
        a.setTypedDataDigest(DIGEST);
        pendingRows.add(a);
        contract.setStatus(ContractStatus.ACTIVE);
        milestone.setStatus(MilestoneStatus.IN_PROGRESS);
        row("mutual-refund", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.REFUNDED, "5000000", CLIENT_WALLET));
        AvalancheContractView v = service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.REFUNDED, milestone.getStatus());
        assertTrue(pendingRows.isEmpty());
        assertNull(v.mutualRefund());
    }

    // ---- Phase 3 D-B notifications slice ----------------------------------------------------------------------------

    @Test
    void settlementNotificationsAreOffByDefault() {
        contract.setStatus(ContractStatus.ACTIVE);
        milestone.setStatus(MilestoneStatus.SUBMITTED);
        row("release", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.RELEASED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.RELEASED, milestone.getStatus());
        Mockito.verifyNoInteractions(notifications);
    }

    @Test
    void whenEnabledBothPartiesAreNotifiedOnceAndOnlyWhenTheEffectIsApplied() {
        properties.setSettlementNotifications(true);
        contract.setJobId(UUID.randomUUID());
        contract.setStatus(ContractStatus.ACTIVE);
        milestone.setStatus(MilestoneStatus.SUBMITTED);
        row("release", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.SUBMITTED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        Mockito.verifyNoInteractions(notifications);

        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.RELEASED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        service.refresh(clientId, false, contract.getId());
        for (UUID party : List.of(clientId, freelancerId)) {
            verify(notifications, times(1)).notify(ArgumentMatchers.eq(party),
                    ArgumentMatchers.eq(NotificationType.RELEASE_CONFIRMED),
                    ArgumentMatchers.contains("mô phỏng"), ArgumentMatchers.contains("không có tiền thật"),
                    ArgumentMatchers.eq(contract.getJobId()), any());
        }
    }

    @Test
    void aRefundNotifiesRefundAndACancelledRevertNotifiesNothing() {
        properties.setSettlementNotifications(true);
        contract.setStatus(ContractStatus.DISPUTED);
        milestone.setStatus(MilestoneStatus.DISPUTED);
        row("resolve", TransactionStatus.REVERTED);
        row("mutual-refund", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.REFUNDED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        verify(notifications, times(2)).notify(any(), ArgumentMatchers.eq(NotificationType.REFUND_CONFIRMED),
                anyString(), anyString(), any(), any());
        verify(notifications, never()).notify(any(), ArgumentMatchers.eq(NotificationType.RELEASE_CONFIRMED),
                anyString(), anyString(), any(), any());
    }

    // ---- A2 finding: the job follows the applied escrow outcome ------------------------------------------------------

    private Job jobOf(JobStatus status) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setStatus(status);
        contract.setJobId(job.getId());
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }

    @Test
    void anAppliedFundStartsTheJobAndAReleaseCompletesIt() {
        Job job = jobOf(JobStatus.AWAITING_PAYMENT);
        AvalancheChainTransaction fund = row("fund", TransactionStatus.SUBMITTED);
        when(gateway.transactionStatus(any())).thenReturn(result(TransactionStatus.CONFIRMED));
        service.refresh(clientId, false, contract.getId());
        assertEquals(JobStatus.AWAITING_PAYMENT, job.getStatus(), "CONFIRMED moves nothing");

        when(gateway.transactionStatus(any())).thenReturn(result(TransactionStatus.FINALIZED));
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.FUNDED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertTrue(fund.isBusinessApplied());
        assertEquals(JobStatus.IN_PROGRESS, job.getStatus());

        milestone.setStatus(MilestoneStatus.SUBMITTED);
        row("release", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.RELEASED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertEquals(JobStatus.COMPLETED, job.getStatus());
    }

    @Test
    void anAppliedRefundCancelsTheJobAndAMismatchChangesNothing() {
        Job job = jobOf(JobStatus.IN_PROGRESS);
        contract.setStatus(ContractStatus.ACTIVE);
        milestone.setStatus(MilestoneStatus.IN_PROGRESS);
        row("mutual-refund", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.FUNDED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertEquals(JobStatus.IN_PROGRESS, job.getStatus(), "CHAIN_MISMATCH applies nothing");
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.REFUNDED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertEquals(JobStatus.CANCELLED, job.getStatus());
    }

    // ---- P3-C4 stuck-and-reported escrow -------------------------------------------------------------------------------

    @Test
    void issuerAlertsAreShownForAnExistingEscrowAndAReportFailureNeverBreaksTheView() {
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.FUNDED, "5000000", CLIENT_WALLET));
        List<IssuerAlert> alerts = List.of(
                new IssuerAlert("FREELANCER_BLACKLISTED", FREELANCER_WALLET, List.of("release")));
        when(gateway.issuerAlerts(any())).thenReturn(alerts);
        assertEquals(alerts, service.status(clientId, false, contract.getId()).issuerAlerts());

        when(gateway.issuerAlerts(any())).thenThrow(new AvalancheGatewayException("down", false, null, null));
        AvalancheContractView v = service.status(clientId, false, contract.getId());
        assertNull(v.issuerAlerts(), "unknown, not 'nothing blocks'");
        assertTrue(v.avalancheEnabled());
    }

    @Test
    void noEscrowYetMeansNoAlertsAndNoReportCall() {
        when(gateway.escrowSnapshot(any())).thenReturn(null);
        assertTrue(service.status(clientId, false, contract.getId()).issuerAlerts().isEmpty());
        verify(gateway, never()).issuerAlerts(any());
    }

    @Test
    void onlyParticipantsAndAdministratorsSeeTheStatus() {
        assertRefused(() -> service.status(strangerId, false, contract.getId()));
        when(gateway.escrowSnapshot(any())).thenReturn(null);
        assertEquals(NETWORK, service.status(clientId, false, contract.getId()).networkId());
        assertTrue(service.status(strangerId, true, contract.getId()).avalancheEnabled());
        // a contract that has not chosen the rail: offered only while the choice is still valid, never as an Avalanche contract
        contract.setPaymentNetworkId(null);
        contract.setStatus(ContractStatus.PENDING_FUNDING);
        AvalancheContractView open = service.status(clientId, false, contract.getId());
        assertTrue(open.avalancheEnabled());
        assertNull(open.networkId());
        assertNull(open.chain());
        contract.setStatus(ContractStatus.ACTIVE);
        assertFalse(service.status(clientId, false, contract.getId()).avalancheEnabled(), "not offered once funding began");
        contract.setStatus(ContractStatus.PENDING_FUNDING);
        contract.setPaymentNetworkId("solana-devnet");
        AvalancheContractView other = service.status(clientId, false, contract.getId());
        assertFalse(other.avalancheEnabled(), "not offered when another network was chosen");
        assertNull(other.networkId(), "a non-Avalanche network id is never reported as the Avalanche network");
    }

    // ---- R12 mutual settlement of a Disputed escrow (owner decision 2026-11-05) ---------------------------------------

    private AvalancheActionInput settle(UUID disputeId, Boolean release, Long deadline) {
        return new AvalancheActionInput(null, null, disputeId, null, null, null, null, deadline, release);
    }

    @Test
    void mutualSettleNeedsAPartyARecordedUnresolvedDisputeAndAnOutcome() throws Exception {
        ContractDispute d = dispute(clientId, DisputeStatus.UNDER_REVIEW);
        Map<String, Object> p = authorizer.authorize(freelancerId, false, contract, "mutual-settle", settle(d.getId(), true, null));
        assertEquals(true, p.get("releaseToFreelancer"));
        assertEquals(sha("avalanche-settlement:" + d.getId() + ":RELEASE"), p.get("resolutionHash"), "the settlement record is derived, never supplied");
        assertEquals(sha("avalanche-settlement:" + d.getId() + ":REFUND"),
                authorizer.authorize(clientId, false, contract, "mutual-settle", settle(d.getId(), false, null)).get("resolutionHash"));
        assertRefused(() -> authorizer.authorize(strangerId, false, contract, "mutual-settle", settle(d.getId(), true, null)));
        assertRefused(() -> authorizer.authorize(strangerId, true, contract, "mutual-settle", settle(d.getId(), true, null)));
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "mutual-settle", settle(d.getId(), null, null)));
        assertRefused(() -> authorizer.authorize(clientId, false, contract, "mutual-settle", settle(null, true, null)));
        for (DisputeStatus closed : List.of(DisputeStatus.RESOLVED_RELEASE, DisputeStatus.RESOLVED_REFUND, DisputeStatus.CANCELLED)) {
            d.setStatus(closed);
            assertRefused(() -> authorizer.authorize(clientId, false, contract, "mutual-settle", settle(d.getId(), true, null)));
        }
    }

    @Test
    void settlementSignaturesArePairedPerOutcomeAndOnlyACompletePairIsSent() {
        ContractDispute d = dispute(clientId, DisputeStatus.OPEN);
        long deadline = Instant.now().getEpochSecond() + 300;
        gatewayExpects(DIGEST);
        when(gateway.escrowSnapshot(any())).thenReturn(null);
        AvalancheContractView v = service.submitMutualSettlementSignature(clientId, contract.getId(), d.getId(), true, deadline, DIGEST, CLIENT_SIG);
        assertEquals(1, v.mutualSettlements().size());
        assertTrue(v.mutualSettlements().get(0).releaseToFreelancer());
        assertTrue(v.mutualSettlements().get(0).clientSigned());
        assertFalse(v.mutualSettlements().get(0).freelancerSigned());
        assertNull(v.mutualRefund(), "a settlement proposal is not a mutual refund");
        assertFalse(authorizer.authorize(clientId, false, contract, "mutual-settle", settle(d.getId(), true, null)).containsKey("clientSignature"),
                "one signature is never sent");

        String refundDigest = "0x" + "d3".repeat(32);
        gatewayExpects(refundDigest);
        v = service.submitMutualSettlementSignature(freelancerId, contract.getId(), d.getId(), false, deadline, refundDigest, FREELANCER_SIG);
        assertEquals(2, v.mutualSettlements().size(), "a different outcome is a separate proposal, never combined");

        gatewayExpects(DIGEST);
        service.submitMutualSettlementSignature(freelancerId, contract.getId(), d.getId(), true, deadline, DIGEST, FREELANCER_SIG);
        Map<String, Object> both = authorizer.authorize(clientId, false, contract, "mutual-settle", settle(d.getId(), true, null));
        assertEquals(CLIENT_SIG, both.get("clientSignature"));
        assertEquals(FREELANCER_SIG, both.get("freelancerSignature"));
        assertEquals(deadline, both.get("authorizationDeadline"));
        assertFalse(authorizer.authorize(clientId, false, contract, "mutual-settle", settle(d.getId(), false, null)).containsKey("clientSignature"),
                "the refund proposal has only the freelancer's signature");

        assertRefused(() -> service.submitMutualSettlementSignature(strangerId, contract.getId(), d.getId(), true, deadline, DIGEST, CLIENT_SIG));
        assertRefused(() -> service.submitMutualSettlementSignature(clientId, contract.getId(), d.getId(), true, deadline, "0x" + "ee".repeat(32), CLIENT_SIG));
    }

    @Test
    void aFinalizedSettlementFollowsTheChainResolvesTheDisputeAndSpendsTheProposals() {
        ContractDispute d = dispute(clientId, DisputeStatus.UNDER_REVIEW);
        when(disputes.findFirstByContractIdAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.of(d));
        for (String key : List.of("mutual-settle:release", "mutual-settle:refund")) {
            AvalanchePendingAuthorization a = new AvalanchePendingAuthorization();
            a.setContractId(contract.getId());
            a.setAction(key);
            a.setAuthorizationDeadline(Instant.now().getEpochSecond() + 300);
            a.setTypedDataDigest(DIGEST);
            pendingRows.add(a);
        }
        contract.setStatus(ContractStatus.ACTIVE);
        milestone.setStatus(MilestoneStatus.SUBMITTED);
        AvalancheChainTransaction r = row("mutual-settle", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.DISPUTED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertEquals("CHAIN_MISMATCH", r.getErrorClass(), "nothing is applied while the chain still shows Disputed");
        assertEquals(DisputeStatus.UNDER_REVIEW, d.getStatus());

        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.RELEASED, "5000000", CLIENT_WALLET));
        AvalancheContractView v = service.refresh(clientId, false, contract.getId());
        assertEquals(MilestoneStatus.RELEASED, milestone.getStatus());
        assertEquals(ContractStatus.COMPLETED, contract.getStatus());
        assertEquals(DisputeStatus.RESOLVED_RELEASE, d.getStatus());
        assertTrue(pendingRows.isEmpty());
        assertTrue(v.mutualSettlements().isEmpty());
    }

    @Test
    void anArbiterResolveClosesTheRecordedDisputeAndADisputeIsAnchoredOnlyWhenTheChainReadsDisputed() {
        ContractDispute d = dispute(clientId, DisputeStatus.DECISION_PENDING_REFUND);
        when(disputes.findFirstByContractIdAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.of(d));
        contract.setStatus(ContractStatus.DISPUTED);
        milestone.setStatus(MilestoneStatus.DISPUTED);
        AvalancheChainTransaction anchor = row("dispute", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.SUBMITTED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertEquals("CHAIN_MISMATCH", anchor.getErrorClass(), "a dispute is not anchored while the chain is not Disputed");
        assertFalse(anchor.isBusinessApplied());

        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.DISPUTED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertTrue(anchor.isBusinessApplied());
        assertEquals(DisputeStatus.DECISION_PENDING_REFUND, d.getStatus());

        AvalancheChainTransaction resolve = row("resolve", TransactionStatus.FINALIZED);
        when(gateway.escrowSnapshot(any())).thenReturn(snapshot(NeutralEscrowStatus.REFUNDED, "5000000", CLIENT_WALLET));
        service.refresh(clientId, false, contract.getId());
        assertTrue(resolve.isBusinessApplied());
        assertEquals(MilestoneStatus.REFUNDED, milestone.getStatus());
        assertEquals(ContractStatus.CANCELLED, contract.getStatus());
        assertEquals(DisputeStatus.RESOLVED_REFUND, d.getStatus());
    }

    @Test
    void anExecuteWithoutAnIdempotencyKeyIsItsOwnOperationAndNeverCollidesWithAnother() {
        contract.setStatus(ContractStatus.PENDING_FUNDING);
        Set<String> keys = new HashSet<>();
        when(chainTransactions.findByNetworkIdAndOperationKey(any(), any())).thenReturn(Optional.empty());
        when(chainTransactions.save(any(AvalancheChainTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(chainTransactions.saveAndFlush(any(AvalancheChainTransaction.class))).thenAnswer(i -> {
            AvalancheChainTransaction t = i.getArgument(0);
            keys.add(t.getOperationKey());
            return t;
        });
        for (int i = 0; i < 3; i++) {
            try {
                service.execute(clientId, false, contract.getId(), "fund", null);
            } catch (RuntimeException e) {
                // the gateway stub may refuse; only the recorded operation keys matter here
            }
        }
        assertEquals(3, keys.size(), "three calls without a key are three operations");
        assertTrue(keys.stream().allMatch(k -> k.startsWith(contract.getId() + ":fund:")));
        verify(chainTransactions, never()).countByContractIdAndAction(any(), any());
    }
}

