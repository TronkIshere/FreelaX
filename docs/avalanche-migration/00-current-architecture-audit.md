# 00. Current architecture audit

Source: working tree `master` @ `0f263b7`, read-only inspection. Paths are repo-relative. Production data was not available (MySQL was unreachable in earlier audits), so which rails are populated is **unknown**.

## 1. Modules and runtime topology
- Maven reactor: `payment-backend`, `marketplace-backend`, `misa-backend`, `solana-integration` (Spring Boot 3.5.16, Java 17 target, built with JDK 21). **No Maven dependencies between modules**; they communicate over HTTP.
- `solana-stablecoin-payout/` (Anchor/Rust + TS tests) is not a Maven module. `frontend/` is Vite/React 18.
- Topology: frontend -> marketplace (:9191) -> payment-backend (:9190, fiat mocks, no chain code), misa (:9192, tax), solana-integration gateway (:9193, own `solana_gateway` schema) -> Solana RPC.
- Chain config: `solana-cpr.*` (marketplace `application.yml`), `solana.*` (gateway), `SOLANA_*` env in `.env.example` files and `docker-compose.yml`, `VITE_SOLANA_CLUSTER` (frontend Dockerfile).

## 2. Blockchain port today
`marketplace-backend/.../blockchain/`: `BlockchainGateway` has **3 methods** (`network()`, `config()`, `onrampReceiptExists()`), records `AssetReference`, `BlockchainAddress`, `BlockchainNetwork`, `EscrowReference`, `EscrowState`, `ChainConfig`, and `solana/SolanaGatewayAdapter`. Only `UnifiedUsdFundingService` uses it, and it still injects the concrete `SolanaOnRampProvider`. `SolanaGatewayAdapter.escrowState` is also called statically by `SolanaEscrowFundingService`, bypassing the interface. There is no `TransactionReference`.

Classes injecting `SolanaCprClient` directly: `SolanaOnRampProvider`, `ContractDisputeService`, `ContractSubmissionService`, `ClientPaymentServiceImpl`, `OnChainOffRampServiceImpl`, `VndPayoutServiceImpl`, `LocalAutoWalletService`, `PaymentFlowService`, `SolanaEscrowFundingService`, `SolanaEscrowTimeoutService`, `SolanaEscrowWorkflowService`, `UnifiedExitService`, `UnifiedFundingExpiryService`, `UnifiedReconciliationService`, plus the adapter. Solana status strings (`Funded`, `Submitted`, `Revision`, `Disputed`, `Released`, `Refunded`) are compared in business code.

## 3. Gateway (`solana-integration`)
- Builds legacy Solana transactions, signs with local keys only (`LocalSignerRegistry`, local RPC hosts only; no KMS/HSM implementation exists), stores `TransactionBuild` (message hash, blockhash, expiry), and `submit()` verifies the user-signed message hash, expiry and blockhash height.
- Endpoints under `/api/v1/solana`: config; invoices; escrows (fund, `actions/{action}`, mutual-refund); rates; withdrawals; onramp-receipts; token-balances; `transactions/{sig}`; `transactions/submit`; admin config and mock-onramp writes.
- **No schedulers, no confirmation polling, no event subscription.** It returns signature status only. All UNKNOWN/retry/idempotency logic lives in marketplace.
- Codecs (`LegacyTransactionCodec`, `AnchorAccountDecoder`, `BorshReader`, `SolanaAddresses`, `InvoicePaymentsInstructions`, `SolanaRpcClient`) are Solana-only; nothing reusable on EVM.
- Fees are sponsored by a system fee payer, which also sponsors rent.

## 4. Anchor program `invoice_payments`
ID `2Tx2faZU1siV1xvKMxbRN1VesgXftjM3Lff3Nwn69oqb`. Escrow instructions: `fund_milestone_escrow`, `request_escrow_extension`, `approve_escrow_extension`, `submit_escrow_work`, `request_escrow_revision`, `open_escrow_dispute`, `settle_milestone_escrow`, `refund_mutual_escrow`. Non-escrow: config, invoice, rate snapshot, off-ramp, mock on-ramp.

Rules **enforced on-chain** (to be preserved in any replacement):
- States: Funded, Submitted, Revision, Disputed, Released, Refunded. Key: milestone UUID (16 bytes).
- Fund needs client **and** marketplace authority (= config admin) signatures; the arbiter is snapshotted from `config.admin` at fund time. The 72h review window and `max_revisions = 2` are **backend-supplied parameters**; the program only bounds the window to 24-168h and revisions to at most 2. Each escrow has its own vault token account (ATA owned by the escrow PDA), i.e. physical per-escrow custody. `paused` blocks only `fund` (settle, refund and dispute stay open). Review window 24-168h (unified rail uses 72h only); legacy adds 24h grace above 500 USDC; max 2 revisions; one extension of at most 7 days.
- Submit needs freelancer + marketplace authority. Revision needs client + authority and must precede `review_due_at`.
- Dispute: from Submitted (before deadline), Funded (only after delivery due date), Revision.
- Settle: Disputed -> arbiter only with non-zero resolution hash; otherwise release by client, or by **anyone after `review_due_at`** (auto-claim). Mutual refund needs both parties.
- No fee logic on-chain (3% platform fee and withholding are computed in Java at withdrawal). **No escrow events emitted**; state is read from the account. One admin key is config admin, arbiter, co-signer, auto-claimer and dispute resolver.

## 5. Business vs chain boundary
- `PaymentFlowService` is a ledger/timeline (`PaymentFlow` + 11 step kinds), not the state machine. `currentChainTerms()` reads gateway config; `confirmChainSettlement` records Released/Refunded.
- Business code decides who may act and the payload hash (`ContractSubmissionService.escrow*Hash`), builds the call, then the contract enforces timing/state. Chain execution begins at `SolanaCprClient`.
- `SolanaEscrowTimeoutService` (30s scheduler): cancels unfunded escrows (48h + 15 min blockhash grace), auto-claims after review deadline, sends `resolve-release/refund` after an admin decision, re-sends after a 15 min not-found timeout (`settlement_retry_pending`).
- UNKNOWN pattern: persist deterministic ids and signature, mark step UNKNOWN on non-definitive error (`SolanaCprException.definitive`), re-read by deterministic key, `retryAfter`, 300s pending expiry. Its safety reasoning leans on **blockhash expiry and PDA idempotency**, which do not exist on EVM.
- `UnifiedUsdFundingService` (deterministic purchase id from flow UUID, receipt PDA = no double mint), `UnifiedExitService` (withdrawal + fiat exit), `UnifiedReconciliationService` / `UnifiedFundingExpiryService`.
- Legacy payout: `SettlementDownstreamService` stages (on-ramp, publish rate, invoice, pay, off-ramp request, VND completion, MISA tax) with `SettlementStageStatus`.
- MISA already stores `blockchain` and `transaction_hash`; marketplace hard-codes `"solana"` in `SettlementTaxService` (~L80) and `TaxCertificateServiceImpl` (~L60). `JobServiceImpl` (~L628) builds Solana explorer URLs.

## 6. Payment rails
| Rail | Chain infrastructure | Migration need |
|---|---|---|
| null / legacy | Funding via payment-backend mock; chain used downstream for payout after fiat release | Partial: only the downstream payout |
| `PARTNER_ESCROW_MOCK` | Escrow funding independent of chain; downstream path **not traced** (open) | None or partial (verify) |
| `SOLANA_ESCROW` | Full (client-signed fund, on-chain release) | Full |
| `UNIFIED_USDC_PAYOUT` | Full, most coupled (mock on-ramp, escrow, off-ramp); `payment-flow.cutover-enabled` routes new jobs here | Full |
`payment-backend` has no chain code and needs no migration. Decision C (revision 2): first-release Avalanche scope is the escrow part of `SOLANA_ESCROW` and `UNIFIED_USDC_PAYOUT`; the legacy downstream payout and historical Solana references are preserved ([03](03-target-architecture.md) section 11).

Escrow ambiguities found in the current program (recorded, not resolved; see [07](07-open-questions.md)): the Revision state has no deadline for resubmission; no unilateral refund exists after a missed delivery date (only dispute or mutual refund); dispute resolution is binary (full release or full refund); a pending extension request never expires; mutual refund is unavailable once Disputed. Legacy payout services must not be removed without SQL evidence (queries in [ADR 0001](../adr/0001-solana-to-avalanche-migration.md)).

## 7. Database
Flyway V1-V5 (marketplace), V1 each in payment-backend and gateway; misa migrations not verified. Chain-bearing: `wallets` (`public_key` varchar 64, encrypted demo key), `escrow_contracts` (PDA, mint, five signature columns, `last_chain_status`), `escrow_action_intents`, `payment_flows` (`network`, `mint`), `payment_flow_steps` (signature, account, idempotency key), `payment_flow_evidence`, `freelancer_payout_records` (~25 PDA/ATA/signature columns, `on_ramp_purchase_id varchar(20)`), `contract_settlements`, `jobs.payment_network/payment_mint`, `work_contracts.payment_rail`.
- Persisted literals: providers `SOLANA_LOCALNET`, `SOLANA_ESCROW`, `UNIFIED_USD_MOCK`; evidence sources `SOLANA_GATEWAY`, `ONRAMP_RECEIPT_AND_ATA`, `ESCROW_PDA_AND_VAULT`; step kinds and statuses; milestone/contract/dispute/funding statuses (full lists in code).
- Widths: 64-char wallet and 100/120-char signature columns fit EVM addresses (42) and tx hashes (66). Hard-coded 6-decimal math (`movePointRight(6)`) matches USDC but should be asserted at runtime.
- Idempotency: `(client_user_id, idempotency_key)`, `release_key`, `payment_flow_steps.idempotency_key`, `escrow_action_intents.build_session_id`, deterministic ids derived from the flow UUID.

## 8. Frontend
Only chain dependency is `@solana/web3.js`; no wagmi/viem/ethers. `frontend/src/escrowWallet.ts` (Phantom, or a server "auto wallet" on localnet) signs a server-built legacy transaction. Callers: `EscrowFunding.tsx`, `ContractLifecycle.tsx`, `Finance.tsx`, `MutualRefund.tsx`, `WalletLink.tsx`, `session.tsx`. Explorer links are hard-coded to `explorer.solana.com`. The wallet-link challenge/verify UI caller was not found. The model "server builds the full tx, wallet only adds a signature" does not transfer to EVM.

## 9. Tests touching Solana
~25 marketplace tests (adapter, client, Unified*, LocalAutoWallet*, WalletLink, settlement, `PaymentFlowMySqlConcurrencyIT`), 6 gateway tests, Anchor TS specs and `scripts/local-*-e2e.ts`, 5 frontend test files and 3 browser scripts.

## 10. Migration boundaries and constraints
Must remain stable: PaymentFlow ledger, 72h window and one-time extension, dispute and admin decisions, fees (3%), withholding/tax, idempotency, UNKNOWN handling and re-query, reconciliation, schedulers, payment-backend and MISA integration, auth, persisted enums, legacy payout behaviour.

Distinct state layers that must not be merged: **business state** (MySQL job/contract/milestone/dispute/flow), **blockchain transaction state** (submitted/accepted/executed/settled), **contract state** (escrow struct), **reconciliation state** (evidence, matched/mismatch).

## 11. Component inventory (condensed)
Action codes: 1 keep Solana legacy, 2 reuse concepts, 3 replace with Avalanche equivalent, 4 behind neutral abstraction, 5 adapt multi-chain, 6 final switch only. Complexity L/M/H.
| Component | Module / path | Active | Cx | Action |
|---|---|---|---|---|
| Anchor escrow instructions | `solana-stablecoin-payout/programs/invoice_payments` | yes | H | 3 (Solidity), 2 (rules) |
| Anchor invoice/rate/off-ramp/mock-onramp | same | yes (legacy, unified exit) | H | 1; 2 for rate/withdrawal concepts |
| Gateway + codecs | `solana-integration` | yes | H | 1; add sibling Avalanche module (3) |
| `LocalSignerRegistry`/`TransactionSigner` | gateway | localnet | M | 5 (signer interface idea; KMS missing) |
| `TransactionBuild` sessions | gateway | yes | M | 2 (EVM uses nonce and EIP-712 deadlines) |
| `SolanaCprClient` + exception | marketplace `client/` | yes (~15 users) | M | 4 then 5 |
| `BlockchainGateway` + adapter | marketplace `blockchain/` | yes | L | 4 (extend per consumer) |
| `SolanaEscrow{Funding,Workflow,Timeout}Service` + scheduler | marketplace `service/` | yes | H | 4 then 5 |
| `Unified{UsdFunding,Exit,Reconciliation,FundingExpiry}Service` | marketplace | yes | M-H | 4 |
| `PaymentFlowService` | marketplace | yes | M | 4 (literals) |
| `Client/OnChainOffRamp/VndPayout/Payout` + `SettlementDownstreamService` | marketplace | yes unless SQL says otherwise | H | 1 (keep) |
| `SolanaOnRampProvider` | `provider/onchain` | yes (mock only) | M | 4 then 3 (test-USDC minting is Fuji-specific) |
| `LocalAutoWalletService`, `WalletLinkService/Controller`, `DemoWalletSeeder` | marketplace | yes (localnet default) | M | 5 |
| `Contract{Submission,Dispute,Review,Cancellation}Service` | marketplace | yes | M | 4 |
| DB columns/entities | marketplace Flyway | yes | M | 5 (forward-only) |
| MISA literal `"solana"` | marketplace + misa | yes | L | 5 |
| payment-backend | payment-backend | yes | L | none |
| `escrowWallet.ts` + callers | frontend | yes | H | 3 behind 4 |
| Explorer links, `VITE_SOLANA_CLUSTER`, compose/env names | frontend/compose | yes | L | 5/6 |

## 12. Verification performed for this audit
Existence of `SolanaCprClient`, `SolanaOnRampProvider`, `ClientPaymentServiceImpl`, `SolanaEscrowTimeoutService`, `UnifiedReconciliationService`, `LocalSignerRegistry`, and `BlockchainGateway` was confirmed by file search. Other details come from a delegated read-only code audit of the same tree; line numbers are approximate. Unverified: payment-backend internals, misa migrations, partner-rail downstream path, runtime data mix.
