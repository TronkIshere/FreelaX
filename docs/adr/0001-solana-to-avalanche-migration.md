# ADR 0001: Solana to Avalanche C-Chain migration

Status: Proposed. No Avalanche code exists yet.

## Context
- Solana is reached only through `marketplace-backend` `client/SolanaCprClient` -> `solana-integration` gateway -> Anchor program `invoice_payments`.
- Solana types leak into business code: `dto/{request,response}/solana/*`, `Wallet.publicKey`/`demoPrivateKey`, `FreelancerPayoutRecord` wallet fields, `Job.paymentNetwork`/`paymentMint`, `PaymentFlowStep`, `Unified*Service`, `Solana*Service`, `LocalAutoWalletService`, and `frontend/src/escrowWallet.ts` (`@solana/web3.js`, Phantom).
- Four payment rails coexist: legacy job-level (`payment_flow_version` null/0), `PARTNER_ESCROW_MOCK`, `SOLANA_ESCROW`, `UNIFIED_USDC_PAYOUT`. `SOLANA_ESCROW` and null-rail contracts reach `SettlementDownstreamService` and the legacy payout services.
- The FreelaX MySQL database was unavailable during the audit, so the existing data mix is unknown.

## Decisions
1. Keep Solana working until an Avalanche path is implemented and verified. Nothing is removed in advance.
2. Keep all four rails and the legacy payout services. Retirement requires the SQL evidence below.
3. Introduce a chain-neutral port in `marketplace-backend` and move Solana behind an adapter before any Avalanche code is written. No fake Avalanche adapter.
4. Do not copy Solana account and finality assumptions to EVM. Reconciliation keys on transaction hash plus log index; build sessions and partially signed transactions are replaced by wallet-signed EIP-712 or direct contract calls.
5. Network, USDC and RPC values come from official Avalanche and Circle documentation at implementation time. None are recorded here.

## Port (to implement in Phase 1)
Operations: network config, address validation, asset balance, build and submit escrow fund, escrow actions (submit work, request revision, dispute, settle, refund, extension), withdrawal build/submit/read, transaction status, escrow state, settlement reference.

Neutral types: `BlockchainAddress`, `TransactionReference`, `BlockchainNetwork`, `AssetReference`, `EscrowReference`. Business interfaces must not expose Base58, Solana signatures, Borsh or Anchor structures.

Adapters: `SolanaGatewayAdapter` (wraps `SolanaCprClient`), later `AvalancheGatewayAdapter`.

Consumers to move behind the port, in order (`UnifiedUsdFundingService` partly done): `Unified*Service`, `PaymentFlowService`, `Solana*Service`, `ClientPaymentServiceImpl`, `OnChainOffRampServiceImpl`, `VndPayoutServiceImpl`, `SolanaOnRampProvider`, `Contract*Service`, `LocalAutoWalletService`.

## Implementation status
- Avalanche contracts, adapter, wallet integration and network configuration: PLANNED (nothing exists in code). Removing Solana or the legacy rails: BLOCKED on database evidence. Gateway port: PARTIALLY IMPLEMENTED (below).
- Done: package `com.marketplace.backend.blockchain` with `BlockchainGateway` (`network()`, `config()`, `onrampReceiptExists()`), neutral records `BlockchainNetwork`, `AssetReference`, `BlockchainAddress`, `EscrowReference`, `ChainConfig`, `EscrowState`, and `blockchain/solana/SolanaGatewayAdapter` over `SolanaCprClient`.
- `UnifiedUsdFundingService` now depends on the port instead of `SolanaCprClient`/`SolanaCprProperties`; `confirmEscrow` takes `EscrowState`. `SolanaEscrowFundingService` maps `SolanaEscrowResult` with `SolanaGatewayAdapter.escrowState`.
- Persisted literals (`SOLANA_LOCALNET`, `ESCROW_PDA_AND_VAULT`) are unchanged.
- Still Solana-bound inside that service: `SolanaOnRampProvider` (next slice, together with `ClientPaymentServiceImpl`).
- Port methods are added only when a consumer is moved. Types such as `TransactionReference` are added with the first consumer that needs them.

## Compatibility and schema
- Flyway changes are forward-only. Any rename of Solana-flavoured columns waits for the SQL evidence.
- No column or migration is dropped.

## Evidence required before retiring a legacy rail
```sql
SELECT payment_flow_version, status, COUNT(*) FROM jobs GROUP BY 1,2;
SELECT payment_rail, status, COUNT(*) FROM work_contracts GROUP BY 1,2;
SELECT on_ramp_status, off_ramp_status, client_payment_status, on_chain_off_ramp_status,
       contract_settlement_id IS NOT NULL AS contract_driven, COUNT(*)
FROM freelancer_payout_records GROUP BY 1,2,3,4,5;
SELECT status, COUNT(*) FROM jobs WHERE checkout_order_id IS NOT NULL GROUP BY 1;
```
Plus unfinished checkout orders in the `payment-backend` schema. Confirm column names against `V1__baseline_schema.sql`.

## Deployment actions introduced with this change
- `LOCAL_WALLET_ENCRYPTION_KEY` (optional, dev/localnet only): new demo wallet keys are encrypted with it; existing rows stay readable through the legacy JWT-derived key. Do not rotate or re-encrypt existing rows without an operator-run migration.
- Startup now fails if `SOLANA_LOCAL_PRIVATE_KEYS` or `LOCAL_WALLET_ENCRYPTION_KEY` is set outside the `dev` profile on `localnet` (marketplace), or if `SOLANA_LOCAL_PRIVATE_KEYS` is set with a non-local RPC host (gateway).
- `exchange-rate.allow-fallback` is true only in the `dev` profile. Other profiles fail the legacy payout step instead of using the 25000 placeholder rate.
- Spring profile still defaults to `dev` in `application.yml` and `docker-compose.yml`. Production must set `SPRING_PROFILES_ACTIVE` explicitly. Changing the default is deferred because it breaks existing local `.env` files.
