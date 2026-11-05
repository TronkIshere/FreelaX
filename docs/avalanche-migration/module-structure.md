# Structure of the two Avalanche modules

Status: structural refactoring of 2026-11-05. Behaviour, HTTP contracts, JSON shapes, error codes, database schema (Flyway) and
persisted values are unchanged. This file describes the **current** layout; the historical plans
([phase-2/01-target-module-architecture.md](phase-2/01-target-module-architecture.md), the M3 to M9 reports) keep the names they used when
they were written.

## Rules both modules follow

- A controller only maps HTTP to a service call. Business and chain orchestration live in a service implementation; a service has an
  interface in `service/` and its implementation in `service/impl/`.
- One file per request or response type (`dto/request`, `dto/response`). Entities are never returned as response types of the gateway;
  persisted rows and wire types stay apart.
- Persistence is in `repository/` (and `entity/`). Constructor injection everywhere; no field injection.
- Error codes and their default messages are declared once (gateway: `applicationException/`; marketplace: `exception/ErrorCode` and
  `blockchain/avalanche/AvalancheErrorClass`). Callers throw a definition, not a literal code.
- Imports, not fully qualified names, in the maintained sources.

## `avalanche-integration` (the gateway, Java 21, `com.freelax.avalanchegateway`)

| Package | Content |
|---|---|
| `config` | Spring configuration and `@ConfigurationProperties` (unchanged apart from the renamed beans in `TxConfig`) |
| `controller` | `AvalancheController` (`/api/v1/avalanche/**`), `StatusController`, `ScanStatusController`: thin, delegate to services |
| `dto/request`, `dto/response` | One record per wire type (`ActionRequest`, `FaucetRequest`, `WalletSignatureRequest`, `SubmitRequest`; fifteen responses including `ApiError`) |
| `service`, `service/impl` | `EscrowActionService` (prepare, execute, faucet), `EscrowQueryService` (config, balance, escrow, alerts, transactions, hash status, token transfer, wallet signature), `GatewayStatusService`, `TransactionSubmissionService`, `TransactionReconcileService` with their `*Impl`; `RequestFields` validates caller-supplied values in one place |
| `repository`, `entity` | `TxRepository`, `ScanRepository` (JDBC, scoped by environment and chain id); `TxRecord`, `TxState`, `ScannedEvent` |
| `applicationException` | `ApplicationErrorDefinition`, `AvalancheErrorDefinitions` (code, HTTP status, default message of every API error), `EnvironmentErrorDefinitions` (fail-closed startup refusals), `ApiException`, `EnvironmentGuardException` |
| `exception` | `ApiExceptionHandler` and the technical exceptions (`ChainClientException`, `LogRangeExceededException`, `UnreadableChainStateException`, `NonceGapException`, `InvalidRequestException`, `IdempotencyConflictException`, `SignerUnavailableException`, `FeeCapExceededException`) |
| `filter` | `InternalApiKeyFilter` (mutating calls need the key; GET calls stay keyless: security finding 9 and its compensating controls are unchanged) |
| `mapper` | `TransactionResponseMapper` |
| `chain`, `contract`, `env`, `scan`, `signer`, `startup`, `tx` | Infrastructure and domain helpers kept where they were: RPC client, ABI and EIP-712 builders, environment guards, log scanner, signers, startup validation, nonce allocation, transitions, error classification |

## `marketplace-backend` (Avalanche part, Java 17, `com.marketplace.backend`)

The base package `com.marketplace.backend` is shared with the legacy rails and was **not** renamed; only the Avalanche files were reorganised.

| Package | Avalanche content |
|---|---|
| `controller` | `AvalancheEscrowController`, `AvalanchePayoutController`, `EvmWalletLinkController` |
| `dto/request/avalanche` | `AvalancheActionInput`, `RecordTransactionRequest`, `MutualRefundSignatureRequest`, `MutualSettlementSignatureRequest`, `WalletChallengeRequest`, `WalletVerifyRequest`, `PayoutTransferRequest` |
| `dto/response/avalanche` | Gateway answers (`Gateway*Response`) and the marketplace's own views (`AvalancheContractView`, `MutualRefundState`, `MutualSettlementState`, `AvalanchePayoutView`, `AvalanchePayoutResult`, `AvalanchePayoutOutcome`, `AvalanchePreparedTransfer`, `EvmWalletChallengeResponse`, `EvmBoundWalletResponse`) |
| `service`, `service/impl` | `AvalancheEscrowService`, `AvalanchePayoutService`, `EvmWalletLinkService` with `AvalancheEscrowServiceImpl`, `AvalanchePayoutServiceImpl`, `EvmWalletLinkServiceImpl`; `AvalancheActionAuthorizer` holds who may do what and builds the gateway parameters from recorded rows only |
| `mapper` | `AvalanchePayoutMapper` (payout row to view, ERC-20 transfer calldata) |
| `entity`, `repository` | `AvalancheChainTransaction`, `AvalanchePendingAuthorization`, `EvmWallet`, `AvalanchePayoutRequest`, `AvalanchePayoutStatus` and their repositories |
| `configuration` | `AvalancheGatewayProperties`, `AvalanchePayoutProperties` |
| `client`, `blockchain/avalanche` | `AvalancheClient`; `AvalancheGatewayAdapter`, `AvalancheEscrowIds`, `AvalancheAction`, `AvalancheErrorClass` |
| `payout/avalanche` | The payout partner port and its adapters (`AvalanchePayoutPartner`, `DisabledAvalanchePayoutPartner`, `LocalMockAvalanchePayoutPartner`), the partner selection (`AvalanchePayoutConfiguration`) and `SolanaRailFeeTaxRules` |

## Where things moved (old to new)

| Before | After |
|---|---|
| gateway `api.Dtos.*` | `dto.request.*`, `dto.response.*` (one file each) |
| gateway `api.EscrowActionService` (469 lines, class) | `service.EscrowActionService` and `EscrowQueryService` interfaces, `service.impl.*Impl` |
| gateway `tx.SubmissionService` (nested exceptions and request) | `service.TransactionSubmissionService` and `service.impl.TransactionSubmissionServiceImpl`; `exception.*`; `dto.request.SubmitRequest` |
| gateway `tx.TxTracker` | `service.TransactionReconcileService` and `service.impl.TransactionReconcileServiceImpl` |
| gateway `api.ApiException`, `env.EnvironmentGuardException` | `applicationException.*` with definitions |
| gateway `tx.TxRepository`, `scan.ScanRepository` | `repository.*` |
| gateway `tx.TxRecord`, `tx.TxState`, `scan.ScannedEvent` | `entity.*` |
| marketplace `dto.response.avalanche.AvalancheResults.*` | `dto.response.avalanche.Gateway*Response` |
| marketplace `AvalancheEscrowService.{Input, ContractView, MutualRefundState, MutualSettlementState}` | `dto.request.avalanche.AvalancheActionInput`, `dto.response.avalanche.*` |
| marketplace `AvalancheEscrowService` (742 lines) | interface, `AvalancheEscrowServiceImpl`, `AvalancheActionAuthorizer` |
| marketplace `payout.avalanche.{AvalanchePayoutService, AvalanchePayoutRequest, AvalanchePayoutStatus, AvalanchePayoutRequestRepository, AvalanchePayoutController, AvalanchePayoutProperties}` | `service`, `service/impl`, `entity`, `repository`, `controller`, `configuration` |
| marketplace string actions in `AvalancheEscrowService` | `blockchain.avalanche.AvalancheAction` (wire spelling unchanged) |

## Known remainders (reported, not changed)

- Legacy marketplace services (`FundingService`, `PartnerEscrowFundingService`, `SolanaEscrowFundingService`, `ContractSubmissionService`,
  `TaxRecordBlockchain`) contain Avalanche guard lines that use fully qualified names; they belong to the legacy rails and were left as they are.
- `EscrowActionServiceImpl.prepareFund` passes the caller's `amount` to `new BigInteger(...)`; a non-numeric amount still ends in `INTERNAL_ERROR`
  (HTTP 500) as before instead of a 400. Not changed in a structural refactoring.
- `WalletSignatureRequest` accepts unknown JSON properties. The original source placed the strict-properties annotation (and a comment saying so) in
  front of `TokenTransferResponse`, so the effective behaviour was lenient; it is preserved.
