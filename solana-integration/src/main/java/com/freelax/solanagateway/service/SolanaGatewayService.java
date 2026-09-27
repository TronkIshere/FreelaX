package com.freelax.solanagateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freelax.solanagateway.api.ApiTypes.Commitment;
import com.freelax.solanagateway.api.ApiTypes.ExecutionMode;
import com.freelax.solanagateway.api.Requests;
import com.freelax.solanagateway.api.Requests.TransactionOptions;
import com.freelax.solanagateway.api.Responses;
import com.freelax.solanagateway.api.Responses.AccountResponse;
import com.freelax.solanagateway.api.Responses.ConfigDto;
import com.freelax.solanagateway.api.Responses.DerivedAccounts;
import com.freelax.solanagateway.api.Responses.InvoiceDto;
import com.freelax.solanagateway.api.Responses.MockOnrampReceiptDto;
import com.freelax.solanagateway.api.Responses.RateSnapshotDto;
import com.freelax.solanagateway.api.Responses.TokenBalanceResponse;
import com.freelax.solanagateway.api.Responses.TransactionBuildResponse;
import com.freelax.solanagateway.api.Responses.TransactionOperationResponse;
import com.freelax.solanagateway.api.Responses.TransactionStatusResponse;
import com.freelax.solanagateway.api.Responses.TransactionSubmittedResponse;
import com.freelax.solanagateway.api.Responses.WithdrawalDto;
import com.freelax.solanagateway.config.SolanaProperties;
import com.freelax.solanagateway.persistence.TransactionBuild;
import com.freelax.solanagateway.persistence.TransactionBuildRepository;
import com.freelax.solanagateway.solana.AnchorAccountDecoder;
import com.freelax.solanagateway.solana.InvoicePaymentsInstructions;
import com.freelax.solanagateway.solana.LegacyTransactionCodec;
import com.freelax.solanagateway.solana.LocalSignerRegistry;
import com.freelax.solanagateway.solana.SolanaAddresses;
import com.freelax.solanagateway.solana.SolanaRpcClient;
import com.freelax.solanagateway.solana.SolanaValueCodec;
import com.freelax.solanagateway.exception.GatewayException;
import jakarta.transaction.Transactional;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import org.p2p.solanaj.programs.SystemProgram;
import org.p2p.solanaj.utils.Base58;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SolanaGatewayService {

    private static final int CONFIG_ACCOUNT_SIZE = 219;
    private static final int INVOICE_ACCOUNT_SIZE = 179;
    private static final int RATE_ACCOUNT_SIZE = 121;
    private static final int WITHDRAWAL_ACCOUNT_SIZE = 296;
    private static final int RECEIPT_ACCOUNT_SIZE = 201;
    private static final int TOKEN_ACCOUNT_SIZE = 165;

    private final SolanaProperties properties;
    private final SolanaAddresses addresses;
    private final SolanaRpcClient rpc;
    private final AnchorAccountDecoder decoder;
    private final InvoicePaymentsInstructions instructions;
    private final LegacyTransactionCodec transactionCodec;
    private final LocalSignerRegistry signerRegistry;
    private final TransactionBuildRepository buildRepository;
    private final ObjectMapper objectMapper;

    public SolanaGatewayService(SolanaProperties properties, SolanaAddresses addresses,
                                SolanaRpcClient rpc, AnchorAccountDecoder decoder,
                                InvoicePaymentsInstructions instructions,
                                LegacyTransactionCodec transactionCodec,
                                LocalSignerRegistry signerRegistry,
                                TransactionBuildRepository buildRepository,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.addresses = addresses;
        this.rpc = rpc;
        this.decoder = decoder;
        this.instructions = instructions;
        this.transactionCodec = transactionCodec;
        this.signerRegistry = signerRegistry;
        this.buildRepository = buildRepository;
        this.objectMapper = objectMapper;
    }

    public AccountResponse<ConfigDto> config(Commitment commitment) {
        PublicKey address = addresses.config();
        SolanaRpcClient.AccountData account = rpc.getAccountInfo(address.toBase58(), commitment);
        if (account == null) {
            return new AccountResponse<>(false, null);
        }
        requireProgramOwned(address, account);
        return new AccountResponse<>(true, decoder.config(address.toBase58(), account.data()));
    }

    public AccountResponse<InvoiceDto> invoice(String freelancer, String invoiceId,
                                               Commitment commitment) {
        PublicKey owner = SolanaValueCodec.publicKey(freelancer);
        PublicKey address = addresses.invoice(owner, invoiceId);
        SolanaRpcClient.AccountData account = rpc.getAccountInfo(address.toBase58(), commitment);
        if (account == null) {
            return new AccountResponse<>(false, null);
        }
        requireProgramOwned(address, account);
        return new AccountResponse<>(true, decoder.invoice(address.toBase58(), account.data()));
    }

    public AccountResponse<RateSnapshotDto> rate(String rateId, Commitment commitment) {
        PublicKey address = addresses.rate(rateId);
        SolanaRpcClient.AccountData account = rpc.getAccountInfo(address.toBase58(), commitment);
        if (account == null) {
            return new AccountResponse<>(false, null);
        }
        requireProgramOwned(address, account);
        return new AccountResponse<>(true, decoder.rate(address.toBase58(), account.data()));
    }

    public AccountResponse<WithdrawalDto> withdrawal(String freelancer, String withdrawalId,
                                                     Commitment commitment) {
        PublicKey owner = SolanaValueCodec.publicKey(freelancer);
        PublicKey address = addresses.withdrawal(owner, withdrawalId);
        SolanaRpcClient.AccountData account = rpc.getAccountInfo(address.toBase58(), commitment);
        if (account == null) {
            return new AccountResponse<>(false, null);
        }
        requireProgramOwned(address, account);
        return new AccountResponse<>(true, decoder.withdrawal(address.toBase58(), account.data()));
    }

    public AccountResponse<MockOnrampReceiptDto> receipt(String client, String purchaseId,
                                                         Commitment commitment) {
        PublicKey owner = SolanaValueCodec.publicKey(client);
        PublicKey address = addresses.mockOnrampReceipt(owner, purchaseId);
        SolanaRpcClient.AccountData account = rpc.getAccountInfo(address.toBase58(), commitment);
        if (account == null) {
            return new AccountResponse<>(false, null);
        }
        requireProgramOwned(address, account);
        return new AccountResponse<>(true, decoder.receipt(address.toBase58(), account.data()));
    }

    public TokenBalanceResponse tokenBalance(String ownerValue, String mintValue,
                                             Commitment commitment) {
        PublicKey owner = SolanaValueCodec.publicKey(ownerValue);
        PublicKey mint = SolanaValueCodec.publicKey(mintValue);
        PublicKey ata = addresses.ata(owner, mint);
        if (rpc.getAccountInfo(ata.toBase58(), commitment) == null) {
            return new TokenBalanceResponse(false, ata.toBase58(), ownerValue, mintValue,
                    "0", 0, "0");
        }
        JsonNode value = rpc.getTokenAccountBalance(ata.toBase58(), commitment);
        return new TokenBalanceResponse(true, ata.toBase58(), ownerValue, mintValue,
                value.path("amount").asText(), value.path("decimals").asInt(),
                value.path("uiAmountString").asText());
    }

    public TransactionStatusResponse transactionStatus(String signature, boolean includeTransaction,
                                                       Commitment commitment) {
        validateSignature(signature);
        JsonNode status = rpc.signatureStatus(signature);
        if (status == null || status.isNull()) {
            return new TransactionStatusResponse(signature, false, null, null, null,
                    null, null, null);
        }
        Map<String, Object> error = status.path("err").isNull() ? null
                : objectMapper.convertValue(status.path("err"), new TypeReference<>() { });
        String blockTime = null;
        List<String> logs = null;
        if (includeTransaction) {
            JsonNode transaction = rpc.transaction(signature, commitment);
            if (transaction != null && !transaction.isNull()) {
                if (transaction.hasNonNull("blockTime")) {
                    blockTime = transaction.path("blockTime").asText();
                }
                JsonNode logMessages = transaction.path("meta").path("logMessages");
                if (logMessages.isArray()) {
                    List<String> collected = new ArrayList<>();
                    logMessages.forEach(node -> collected.add(node.asText()));
                    logs = List.copyOf(collected);
                }
            }
        }
        Integer confirmations = status.path("confirmations").isNull()
                ? null : status.path("confirmations").asInt();
        return new TransactionStatusResponse(signature, true, status.path("slot").asText(),
                confirmations, nullIfBlank(status.path("confirmationStatus").asText()), error,
                blockTime, logs);
    }

    public TransactionOperationResponse initializeConfig(Requests.InitializeConfigRequest request) {
        PublicKey admin = key(request.admin());
        PublicKey mint = key(request.acceptedMint());
        PublicKey feePayer = feePayer(admin);
        List<TransactionInstruction> list = new ArrayList<>();
        addRentSponsor(list, feePayer, admin, CONFIG_ACCOUNT_SIZE, request.commitment());
        list.add(instructions.initializeConfig(admin, mint, key(request.treasuryAuthority()),
                key(request.rateAuthority()), key(request.oracleAuthority()),
                SolanaValueCodec.i64(request.maxRateAgeSeconds(), "maxRateAgeSeconds")));
        return execute("initialize_config", admin, request, ExecutionMode.send, list,
                derived(Map.of("config", addresses.config(), "programData", addresses.programData())));
    }

    public TransactionOperationResponse updateConfig(Requests.UpdateConfigRequest request) {
        ConfigDto config = requireConfig(request.commitment());
        requireAddress(config.admin(), request.admin(), "Admin does not match Config");
        PublicKey admin = key(request.admin());
        TransactionInstruction instruction = instructions.updateConfig(admin, key(request.acceptedMint()),
                key(request.treasuryAuthority()), key(request.rateAuthority()), key(request.oracleAuthority()),
                SolanaValueCodec.i64(request.maxRateAgeSeconds(), "maxRateAgeSeconds"), request.paused());
        return execute("update_config", admin, request, ExecutionMode.send, List.of(instruction),
                derived(Map.of("config", addresses.config())));
    }

    public TransactionOperationResponse configureMockOnramp(Requests.ConfigureMockOnrampRequest request) {
        ConfigDto config = requireConfig(request.commitment());
        requireAddress(config.admin(), request.admin(), "Admin does not match Config");
        PublicKey admin = key(request.admin());
        return execute("configure_mock_onramp", admin, request, ExecutionMode.send,
                List.of(instructions.configureMockOnramp(admin, key(request.authority()),
                        request.maxAmount(), request.enabled())),
                derived(Map.of("config", addresses.config())));
    }

    public TransactionOperationResponse mockOnramp(Requests.MockOnrampRequest request) {
        ConfigDto config = requireConfig(request.commitment());
        requireAddress(config.mockOnrampAuthority(), request.onrampAuthority(),
                "Mock on-ramp authority does not match Config");
        if (!config.mockOnrampEnabled()) {
            throw conflict("MOCK_ONRAMP_DISABLED", "Mock on-ramp is disabled");
        }
        PublicKey authority = key(request.onrampAuthority());
        PublicKey client = key(request.client());
        PublicKey mint = key(config.acceptedMint());
        PublicKey feePayer = feePayer(authority);
        List<TransactionInstruction> list = new ArrayList<>();
        List<Integer> rentSizes = new ArrayList<>();
        rentSizes.add(RECEIPT_ACCOUNT_SIZE);
        if (rpc.getAccountInfo(addresses.ata(client, mint).toBase58(), request.commitment()) == null) {
            rentSizes.add(TOKEN_ACCOUNT_SIZE);
        }
        addRentSponsor(list, feePayer, authority, request.commitment(),
                rentSizes.stream().mapToInt(Integer::intValue).toArray());
        list.add(instructions.mockOnramp(authority, mint, client, request.purchaseId(), request.usdAmountE6()));
        Map<String, PublicKey> derived = new LinkedHashMap<>();
        derived.put("config", addresses.config());
        derived.put("mockOnrampReceipt", addresses.mockOnrampReceipt(client, request.purchaseId()));
        derived.put("mockOnrampTreasuryAuthority", addresses.mockOnrampTreasuryAuthority());
        derived.put("mockOnrampTreasuryAta", addresses.ata(addresses.mockOnrampTreasuryAuthority(), mint));
        derived.put("clientAta", addresses.ata(client, mint));
        return execute("mock_onramp", authority, request, ExecutionMode.send, list, derived(derived));
    }

    public TransactionOperationResponse publishRate(Requests.PublishRateRequest request) {
        ConfigDto config = requireConfig(request.commitment());
        requireAddress(config.rateAuthority(), request.rateAuthority(),
                "Rate authority does not match Config");
        PublicKey authority = key(request.rateAuthority());
        PublicKey feePayer = feePayer(authority);
        List<TransactionInstruction> list = new ArrayList<>();
        addRentSponsor(list, feePayer, authority, RATE_ACCOUNT_SIZE, request.commitment());
        list.add(instructions.publishRate(authority, request.rateId(), request.usdcUsdE6(),
                request.usdVndE6(), SolanaValueCodec.i64(request.observedAt(), "observedAt"),
                SolanaValueCodec.i64(request.expiresAt(), "expiresAt"),
                SolanaValueCodec.hash32(request.sourceHash(), "sourceHash", false)));
        return execute("publish_rate", authority, request, ExecutionMode.send, list,
                derived(Map.of("config", addresses.config(), "rateSnapshot", addresses.rate(request.rateId()))));
    }

    public TransactionOperationResponse createInvoice(Requests.CreateInvoiceRequest request) {
        requireConfig(request.commitment());
        requireRate(request.rateId(), request.commitment());
        PublicKey freelancer = key(request.freelancer());
        PublicKey feePayer = feePayer(freelancer);
        List<TransactionInstruction> list = new ArrayList<>();
        addRentSponsor(list, feePayer, freelancer, INVOICE_ACCOUNT_SIZE, request.commitment());
        list.add(instructions.createInvoice(freelancer, key(request.client()), request.invoiceId(),
                request.amount(), request.rateId(),
                SolanaValueCodec.i64(request.expiresAt(), "expiresAt")));
        return execute("create_invoice", freelancer, request, ExecutionMode.build, list,
                derived(Map.of("config", addresses.config(),
                        "rateSnapshot", addresses.rate(request.rateId()),
                        "invoice", addresses.invoice(freelancer, request.invoiceId()))));
    }

    public TransactionOperationResponse payInvoice(String freelancerValue, String invoiceId,
                                                   Requests.PayInvoiceRequest request) {
        InvoiceDto invoice = requireInvoice(freelancerValue, invoiceId, request.commitment());
        requireAddress(invoice.client(), request.client(), "Client is not assigned to this Invoice");
        PublicKey client = key(request.client());
        PublicKey freelancer = key(freelancerValue);
        PublicKey mint = key(invoice.mint());
        PublicKey feePayer = feePayer(client);
        List<TransactionInstruction> list = new ArrayList<>();
        list.add(instructions.createAtaIdempotent(feePayer, freelancer, mint));
        list.add(instructions.payInvoice(client, freelancer, mint, invoiceId));
        return execute("pay_invoice", client, request, ExecutionMode.build, list,
                derived(Map.of("config", addresses.config(),
                        "invoice", addresses.invoice(freelancer, invoiceId),
                        "clientAta", addresses.ata(client, mint),
                        "freelancerAta", addresses.ata(freelancer, mint))));
    }

    public TransactionOperationResponse cancelInvoice(String freelancerValue, String invoiceId,
                                                      Requests.InvoiceActionRequest request) {
        InvoiceDto invoice = requireInvoice(freelancerValue, invoiceId, request.commitment());
        requireAddress(invoice.freelancer(), freelancerValue, "Freelancer does not own this Invoice");
        PublicKey freelancer = key(freelancerValue);
        return execute("cancel_invoice", freelancer, request, ExecutionMode.build,
                List.of(instructions.cancelInvoice(freelancer, invoiceId)),
                derived(Map.of("config", addresses.config(),
                        "invoice", addresses.invoice(freelancer, invoiceId))));
    }

    public TransactionOperationResponse closeInvoice(String freelancerValue, String invoiceId,
                                                     Requests.InvoiceActionRequest request) {
        InvoiceDto invoice = requireInvoice(freelancerValue, invoiceId, request.commitment());
        requireAddress(invoice.freelancer(), freelancerValue, "Freelancer does not own this Invoice");
        PublicKey freelancer = key(freelancerValue);
        return execute("close_invoice", freelancer, request, ExecutionMode.build,
                List.of(instructions.closeInvoice(freelancer, invoiceId)),
                derived(Map.of("invoice", addresses.invoice(freelancer, invoiceId))));
    }

    public TransactionOperationResponse requestOfframp(Requests.RequestOfframpRequest request) {
        ConfigDto config = requireConfig(request.commitment());
        requireRate(request.rateId(), request.commitment());
        PublicKey freelancer = key(request.freelancer());
        PublicKey mint = key(config.acceptedMint());
        PublicKey treasury = key(config.treasuryAuthority());
        PublicKey feePayer = feePayer(freelancer);
        List<TransactionInstruction> list = new ArrayList<>();
        if (rpc.getAccountInfo(addresses.ata(treasury, mint).toBase58(), request.commitment()) == null) {
            list.add(instructions.createAtaIdempotent(feePayer, treasury, mint));
        }
        addRentSponsor(list, feePayer, freelancer, WITHDRAWAL_ACCOUNT_SIZE, request.commitment());
        list.add(instructions.requestOfframp(freelancer, request.withdrawalId(), request.rateId(),
                request.tokenAmount(), mint, treasury));
        return execute("request_offramp", freelancer, request, ExecutionMode.build, list,
                derived(Map.of("config", addresses.config(),
                        "rateSnapshot", addresses.rate(request.rateId()),
                        "withdrawalRecord", addresses.withdrawal(freelancer, request.withdrawalId()),
                        "freelancerAta", addresses.ata(freelancer, mint),
                        "treasuryAta", addresses.ata(treasury, mint))));
    }

    public TransactionOperationResponse completeOfframp(String freelancerValue, String withdrawalId,
                                                        Requests.CompleteOfframpRequest request) {
        ConfigDto config = requireConfig(request.commitment());
        requireAddress(config.oracleAuthority(), request.oracleAuthority(),
                "Oracle authority does not match Config");
        requireWithdrawal(freelancerValue, withdrawalId, request.commitment());
        PublicKey oracle = key(request.oracleAuthority());
        PublicKey freelancer = key(freelancerValue);
        return execute("record_offramp", oracle, request, ExecutionMode.send,
                List.of(instructions.recordOfframp(oracle, freelancer, withdrawalId)),
                derived(Map.of("config", addresses.config(),
                        "withdrawalRecord", addresses.withdrawal(freelancer, withdrawalId))));
    }

    public TransactionOperationResponse failOfframp(String freelancerValue, String withdrawalId,
                                                    Requests.MarkOfframpFailedRequest request) {
        ConfigDto config = requireConfig(request.commitment());
        requireAddress(config.oracleAuthority(), request.oracleAuthority(),
                "Oracle authority does not match Config");
        requireWithdrawal(freelancerValue, withdrawalId, request.commitment());
        PublicKey oracle = key(request.oracleAuthority());
        PublicKey freelancer = key(freelancerValue);
        return execute("mark_offramp_failed", oracle, request, ExecutionMode.send,
                List.of(instructions.markOfframpFailed(oracle, freelancer, withdrawalId,
                        SolanaValueCodec.hash32(request.failureHash(), "failureHash", true))),
                derived(Map.of("config", addresses.config(),
                        "withdrawalRecord", addresses.withdrawal(freelancer, withdrawalId))));
    }

    public TransactionOperationResponse resolveOfframp(String freelancerValue, String withdrawalId,
                                                       Requests.ResolveOfframpRequest request) {
        ConfigDto config = requireConfig(request.commitment());
        requireAddress(config.admin(), request.admin(), "Admin does not match Config");
        requireWithdrawal(freelancerValue, withdrawalId, request.commitment());
        PublicKey admin = key(request.admin());
        PublicKey freelancer = key(freelancerValue);
        return execute("resolve_offramp", admin, request, ExecutionMode.send,
                List.of(instructions.resolveOfframp(admin, freelancer, withdrawalId,
                        SolanaValueCodec.hash32(request.resolutionHash(), "resolutionHash", true))),
                derived(Map.of("config", addresses.config(),
                        "withdrawalRecord", addresses.withdrawal(freelancer, withdrawalId))));
    }

    @Transactional
    public Responses.SubmitTransactionResponse submit(Requests.SubmitSignedTransactionRequest request) {
        byte[] serialized;
        try {
            serialized = Base64.getDecoder().decode(request.transactionBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("transactionBase64 is not valid Base64", exception);
        }
        LegacyTransactionCodec.ParsedTransaction parsed = transactionCodec.parse(serialized);
        parsed.verifyAllSignatures();
        String messageHash = SolanaValueCodec.sha256Hex(parsed.message());

        TransactionBuild build = null;
        if (request.buildSessionId() != null && !request.buildSessionId().isBlank()) {
            build = buildRepository.findById(request.buildSessionId())
                    .orElseThrow(() -> new GatewayException(HttpStatus.NOT_FOUND,
                            "BUILD_SESSION_NOT_FOUND", "Unknown build session"));
            if (!build.messageHash().equals(messageHash)) {
                throw conflict("TRANSACTION_MESSAGE_CHANGED",
                        "Signed transaction message does not match the server-built transaction");
            }
            if (build.expiresAt().isBefore(Instant.now())) {
                throw new GatewayException(HttpStatus.GONE, "BUILD_SESSION_EXPIRED",
                        "Build session has expired");
            }
            if (build.submittedSignature() != null) {
                return new Responses.SubmitTransactionResponse(build.submittedSignature());
            }
            long currentHeight = rpc.getBlockHeight(request.preflightCommitment());
            if (currentHeight > build.lastValidBlockHeight()) {
                throw new GatewayException(HttpStatus.GONE, "BLOCKHASH_EXPIRED",
                        "Transaction blockhash has expired; build a new transaction");
            }
        } else if (properties.requireBuildSession()) {
            throw new GatewayException(HttpStatus.BAD_REQUEST, "BUILD_SESSION_REQUIRED",
                    "buildSessionId is required for sponsored transaction submission");
        }

        String signature = rpc.sendTransaction(request.transactionBase64(),
                Boolean.TRUE.equals(request.skipPreflight()), request.preflightCommitment());
        if (build != null) {
            build.submitted(signature);
            buildRepository.save(build);
        }
        return new Responses.SubmitTransactionResponse(signature);
    }

    private TransactionOperationResponse execute(String instructionName, PublicKey businessSigner,
                                                 TransactionOptions options, ExecutionMode defaultMode,
                                                 List<TransactionInstruction> instructionList,
                                                 DerivedAccounts derivedAccounts) {
        ExecutionMode mode = options.mode() == null ? defaultMode : options.mode();
        Commitment commitment = commitment(options.commitment());
        PublicKey feePayer = feePayer(businessSigner);
        SolanaRpcClient.LatestBlockhash latest = rpc.getLatestBlockhash(commitment);
        LegacyTransactionCodec.CompiledTransaction compiled = transactionCodec.compile(
                feePayer, latest.blockhash(), instructionList);

        Map<String, byte[]> signatures = new LinkedHashMap<>();
        for (String requiredSigner : compiled.requiredSigners()) {
            signerRegistry.find(requiredSigner)
                    .ifPresent(signer -> signatures.put(requiredSigner, signer.sign(compiled.message())));
        }

        if (!feePayer.equals(businessSigner) && !signatures.containsKey(feePayer.toBase58())) {
            throw new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "FEE_PAYER_SIGNER_UNAVAILABLE",
                    "Configured system fee payer signer is unavailable");
        }

        if (mode == ExecutionMode.send) {
            List<String> missing = compiled.requiredSigners().stream()
                    .filter(signer -> !signatures.containsKey(signer)).toList();
            if (!missing.isEmpty()) {
                throw new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "SIGNER_UNAVAILABLE",
                        "Backend signer unavailable for: " + String.join(",", missing));
            }
            byte[] serialized = compiled.serialize(signatures);
            String signature = rpc.sendTransaction(Base64.getEncoder().encodeToString(serialized),
                    Boolean.TRUE.equals(options.skipPreflight()), commitment);
            return new TransactionSubmittedResponse("submitted", instructionName, signature, derivedAccounts);
        }

        byte[] serialized = compiled.serialize(signatures);
        String buildId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(properties.buildSessionTtlSeconds(), ChronoUnit.SECONDS);
        buildRepository.save(new TransactionBuild(buildId, instructionName,
                SolanaValueCodec.sha256Hex(compiled.message()), feePayer.toBase58(),
                String.join(",", compiled.requiredSigners()), latest.blockhash(),
                latest.lastValidBlockHeight(), expiresAt));
        return new TransactionBuildResponse("requires_signature", instructionName, buildId,
                Base64.getEncoder().encodeToString(serialized), feePayer.toBase58(), latest.blockhash(),
                latest.lastValidBlockHeight(), compiled.requiredSigners(), derivedAccounts);
    }

    private void addRentSponsor(List<TransactionInstruction> list, PublicKey feePayer,
                                PublicKey anchorPayer, int accountSize, Commitment commitment) {
        addRentSponsor(list, feePayer, anchorPayer, commitment, accountSize);
    }

    private void addRentSponsor(List<TransactionInstruction> list, PublicKey feePayer,
                                PublicKey anchorPayer, Commitment commitment, int... accountSizes) {
        if (feePayer.equals(anchorPayer)) {
            return;
        }
        long required = 0;
        for (int accountSize : accountSizes) {
            required = Math.addExact(required,
                    rpc.getMinimumBalanceForRentExemption(accountSize, commitment));
        }
        long current = rpc.getBalance(anchorPayer.toBase58(), commitment);
        if (current < required) {
            list.add(SystemProgram.transfer(feePayer, anchorPayer, required - current));
        }
    }

    private ConfigDto requireConfig(Commitment commitment) {
        AccountResponse<ConfigDto> response = config(commitment);
        if (!response.exists()) {
            throw new GatewayException(HttpStatus.NOT_FOUND, "CONFIG_NOT_FOUND",
                    "Solana Config PDA does not exist");
        }
        return response.data();
    }

    private InvoiceDto requireInvoice(String freelancer, String invoiceId, Commitment commitment) {
        AccountResponse<InvoiceDto> response = invoice(freelancer, invoiceId, commitment);
        if (!response.exists()) {
            throw new GatewayException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND",
                    "Invoice PDA does not exist");
        }
        return response.data();
    }

    private RateSnapshotDto requireRate(String rateId, Commitment commitment) {
        AccountResponse<RateSnapshotDto> response = rate(rateId, commitment);
        if (!response.exists()) {
            throw new GatewayException(HttpStatus.NOT_FOUND, "RATE_NOT_FOUND",
                    "RateSnapshot PDA does not exist");
        }
        return response.data();
    }

    private WithdrawalDto requireWithdrawal(String freelancer, String withdrawalId,
                                            Commitment commitment) {
        AccountResponse<WithdrawalDto> response = withdrawal(freelancer, withdrawalId, commitment);
        if (!response.exists()) {
            throw new GatewayException(HttpStatus.NOT_FOUND, "WITHDRAWAL_NOT_FOUND",
                    "WithdrawalRecord PDA does not exist");
        }
        return response.data();
    }

    private PublicKey feePayer(PublicKey fallback) {
        String configured = properties.systemFeePayer();
        return configured == null || configured.isBlank() ? fallback : key(configured);
    }

    private PublicKey key(String value) {
        return SolanaValueCodec.publicKey(value);
    }

    private Commitment commitment(Commitment value) {
        return value == null ? Commitment.valueOf(properties.commitment()) : value;
    }

    private void requireAddress(String expected, String actual, String message) {
        if (!key(expected).equals(key(actual))) {
            throw new GatewayException(HttpStatus.FORBIDDEN, "SIGNER_MISMATCH", message);
        }
    }

    private void requireProgramOwned(PublicKey address, SolanaRpcClient.AccountData account) {
        if (!properties.programId().equals(account.owner())) {
            throw new GatewayException(HttpStatus.BAD_GATEWAY, "INVALID_ACCOUNT_OWNER",
                    "Account " + address.toBase58() + " is not owned by the configured Solana program");
        }
    }

    private void validateSignature(String signature) {
        try {
            if (Base58.decode(signature).length != 64) {
                throw new IllegalArgumentException("Signature must decode to 64 bytes");
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid transaction signature", exception);
        }
    }

    private GatewayException conflict(String code, String message) {
        return new GatewayException(HttpStatus.CONFLICT, code, message);
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private DerivedAccounts derived(Map<String, PublicKey> values) {
        return new DerivedAccounts(value(values, "config"), value(values, "programData"),
                value(values, "invoice"), value(values, "rateSnapshot"),
                value(values, "withdrawalRecord"), value(values, "mockOnrampReceipt"),
                value(values, "mockOnrampTreasuryAuthority"), value(values, "mockOnrampTreasuryAta"),
                value(values, "clientAta"), value(values, "freelancerAta"), value(values, "treasuryAta"));
    }

    private String value(Map<String, PublicKey> values, String key) {
        PublicKey value = values.get(key);
        return value == null ? null : value.toBase58();
    }
}
