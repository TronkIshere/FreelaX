# P2-M9 validation report: marketplace integration behind a flag

Date: 2026-11-02. Scope source: [03 backend plan, P2-M9](03-backend-implementation-plan.md), [10 legacy coexistence](10-legacy-solana-coexistence.md), owner decisions O-9 (chain identifier on the existing rail model, backward-compatible migration), O-14 (Avalanche for new work; in-flight Solana escrows finish on Solana), O-15 (fiat stays labelled mocks), O-7. The prompt's M9 ("full integration testing") and M11 ("legacy coexistence") are covered by this report, the M12 report and the coexistence evidence below; the repository roadmap is followed.
**No Fuji request, transaction or signer; no Mainnet contact; no real funds. No Solana service was modified** (the only Solana-side change is the `@Primary` wiring annotation added in M8).

## 1. Delivered
| Item | Detail |
|---|---|
| Flyway `V6__avalanche_chain_links.sql` | **additive and forward-only**: `work_contracts.payment_network_id` (nullable; NULL means the existing behaviour) and the new table `avalanche_chain_transactions` (contract, milestone, network, **environment, chain id**, escrow id, action, unique operation key, actor, tx hash, status, error class, a business-applied flag, timestamps). No existing row, column, enum literal or index is changed |
| `AvalancheEscrowService` (flag `avalanche-gateway.enabled`, off by default) | enable the rail (client only, only before funding, **never for a contract that already has a Solana escrow**); status; `prepare` (calldata for the user's own wallet); `execute` (gateway development wallets, LOCAL only); record a wallet-broadcast hash; refresh |
| `AvalancheEscrowController` | `/api/v1/contracts/{id}/avalanche` (status, enable, prepare, execute, record, refresh) under the existing authentication |
| Entity change | one nullable field `paymentNetworkId` on `WorkContract` |

## 2. Authorization before submission (enforced in the service, not in the UI)
| Action | Who | Data used (recorded rows only) |
|---|---|---|
| fund | the client; contract awaiting funding | terms **built from the contract, milestone and linked wallets**: amount equals the recorded budget in base units, delivery date, review window equals the recorded 72 h rule, `maxRevisions`, funding expiry 48 h, no high-value grace; the request carries no term (the input type has no amount, party, date, arbiter, chain id, RPC URL or key field, asserted by a test) |
| submit | the freelancer | evidence hash derived from a **recorded submission of this contract that is in review** |
| revise | the client | feedback hash derived from a **recorded revision request with feedback** |
| dispute | a party | reason hash derived from a **recorded dispute opened by that same user** |
| resolve | administrator only | outcome taken from the **recorded administrator decision** (release only for `DECISION_PENDING_RELEASE`, refund for `..._REFUND`); anything else refused |
| extension request / approve, release, mutual refund | freelancer / client / a party / a party | calldata only; the mutual refund needs both parties' wallet signatures (consent) |
Execution records the attempt **before** the gateway is called, is idempotent by operation key, maps a timeout to UNKNOWN (never failed) and a definitive gateway rejection to FAILED with the gateway's code. **Business state changes only when a transaction is FINALIZED and the chain state, read independently, agrees with the records** (funding checks amount, client wallet, freelancer wallet; release and refund follow the chain's status, not the request); a mismatch is flagged `CHAIN_MISMATCH` and nothing changes; an applied effect is never applied twice.

## 3. Results
| Check | Result |
|---|---|
| New tests: `AvalancheEscrowServiceTest` | 17 pass: rail choice and Solana refusal, authorization matrix for every action, term and hash derivation, idempotency, record-before-call ordering, unknown versus failed, user-broadcast recording, FINALIZED-only settlement and chain mismatch, unreachable gateway during refresh, status visibility |
| Marketplace full suite | **381 run, 0 failed** (364 existing + 17 new); Solana tests unchanged |
| Migration on a throwaway MySQL 8.4 | V1-V5 applied, V6 applied; `work_contracts` differs only by the one new nullable column; a legacy contract row survives with a NULL network; the new table has the designed columns |
| JDK 17 | marketplace and Solana gateway compile on JDK 17 |
| Flag off | no Avalanche bean exists (`ApplicationContextRunner` test from M8) |

## 4. Not done in M9 (explicit)
1. **Fees, MISA tax records, notifications and the downstream payout stages are not wired to the Avalanche rail.** The existing settlement services are specific to the Solana rails and were deliberately not modified; an Avalanche release marks the milestone released and the contract completed but triggers no fee, tax or payout record. This is a gap against "Fees and tax records", "Notifications" in the prompt's business scope and needs a decision (see section 5).
2. The marketplace does not yet mirror the on-chain `Submitted/Revision/Disputed` transitions into milestone or contract status; those transitions still come from the existing marketplace flows (submission, review, dispute), and the chain actions follow them.
3. No full cross-service run (marketplace plus gateway plus Anvil in one environment) was executed: the marketplace is covered against a mocked gateway and an HTTP stub, the gateway against the real contract; the contract between them is checked by shape assertions on both sides.
4. Explorer links, the hard-coded `"solana"` literal in MISA/tax flows and per-record chain labelling in existing screens are unchanged.

## 5. Decisions needed
- Fee, tax and payout treatment for an Avalanche release (keep as labelled mocks, wire to the existing downstream stages, or leave off the rail).
- Whether transitions Submitted/Revision/Disputed should update milestone status from chain events (a scanner-driven mirror) or stay driven by the marketplace flows.
