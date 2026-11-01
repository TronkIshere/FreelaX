# 10. Legacy Solana coexistence and migration gates (plan only)

Principle (ADR 0001, simulation scope): keep Solana working until Avalanche is implemented and verified; remove nothing in advance; no legacy payout path is deleted and no existing record is migrated without read-only database evidence **and** explicit owner approval.

## 1. What stays untouched
All Solana modules (`solana-integration`, `solana-stablecoin-payout`), `SolanaCprClient`, `SolanaGatewayAdapter`, Solana-flavoured services and DTOs, persisted literals (`SOLANA_LOCALNET`, `ESCROW_PDA_AND_VAULT`, rail literals), existing `network`, `mint`, `escrow_address` and `*_signature` columns, the Solana frontend path (`escrowWallet.ts`, `@solana/web3.js`) and historical explorer references. Existing Solana tests must pass unchanged at every milestone.

## 2. Coexistence strategy
1. **Port, not replacement:** both adapters implement the neutral `BlockchainGateway`; the marketplace selects by `BlockchainNetwork` per record, not by a global switch. Avalanche stays disabled or secondary until M9 acceptance.
2. **Additive persistence:** new columns (environment, chain id, tx hash, nonce, block) are added forward-only; old rows keep their values and remain readable; new rows carry an explicit chain/network value.
3. **Explorer links, MISA `blockchain` field and tax certificates** choose the chain **per record** instead of the hard-coded `"solana"` literal (P2-M9), without changing historical rows.
4. **Rails:** the first Avalanche scope is the escrow part of `SOLANA_ESCROW` and `UNIFIED_USDC_PAYOUT`. `PARTNER_ESCROW_MOCK` and the legacy null rail's fiat funding need no chain work. The legacy downstream payout (invoice, off-ramp, tax) stays on Solana. Decision O-9 settles whether a chain column or a new rail literal marks Avalanche records.
5. **In-flight Solana escrows** finish on Solana (decision O-14); new work uses Avalanche only after the flag is enabled by the owner.
6. **Frontend:** the neutral wallet interface keeps `solanaWallet` unchanged next to `evmWallet`; Solana UI is not removed.

## 3. Migration gates (all must hold before any legacy path is touched)
| Gate | Evidence |
|---|---|
| G1 read-only DB evidence collected (rail mix, unfinished checkouts) | operator results from [db-rail-evidence-request.md](../phase-1/db-rail-evidence-request.md), labelled per environment |
| G2 Avalanche path accepted on Anvil (M12) and, if the owner wants public demonstrations, on Fuji | [07](07-test-strategy.md) |
| G3 historical Solana data proven readable through the new neutral views | regression tests on a database copy |
| G4 explicit owner approval naming the path and the action | recorded decision |
| G5 rollback proven: flag off restores the Solana-only behaviour with untouched Solana modules | M9/M12 drills |
Until all gates hold, legacy payout services (`ClientPaymentServiceImpl`, `OnChainOffRampServiceImpl`, `VndPayoutServiceImpl`, `SettlementDownstreamService` and related) are **not** modified, renamed or deleted.

## 4. Risks specific to coexistence
Hidden Solana literals in business code (status strings such as `"Funded"`/`"Released"`), the hard-coded `"solana"` in tax/MISA flows, and two UNKNOWN-handling models. Mitigation: normalise through the neutral `EscrowState` inside adapters, fault-injection tests per adapter, regression matrix per rail, and no shared mutable state between adapters.
