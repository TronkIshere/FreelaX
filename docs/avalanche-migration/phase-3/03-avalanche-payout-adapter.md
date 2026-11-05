# Phase 3: non-custodial Avalanche payout adapter (2026-11-04)

**Status: IMPLEMENTED (LOCAL_SIMULATION, labelled mock partner). Real partner integration NOT IMPLEMENTED and disabled.**
Authority: Lead instruction 2026-11-04 "R6, R12 and Avalanche payout", section 3 (engineering execution under delegated
authority; R6 owner approval pending). Simulation scope unchanged: no real funds, no real fiat payout, no Mainnet.

## 1. Why a separate adapter
The existing payout pipeline (`SettlementDownstreamService`: on-ramp, client payment, `OnChainOffRampService` withdrawal
through the Solana gateway, `VndPayoutService`, `SettlementTaxService`) is Solana-bound from its first step (L-017 analysis).
Reusing it for Avalanche would either fake Solana steps or reroute value across chains. The Lead's design rules are:
preserve the Solana flow, build an independent Avalanche adapter, create no FreelaX custody model, never fall back between
chains, require explicit partner support for the token and network, return an explicit unsupported-rail result otherwise,
keep on-chain transfer confirmation apart from fiat payout confirmation, and invent no fee formula, tax rule or settlement claim.
The adapter follows all of them. `SettlementDownstreamService` and every Solana class are untouched.

## 2. Money flow (non-custodial)
1. The escrow releases (client release, permissionless release or arbiter decision): the full amount is in the freelancer's
   **own** EVM wallet (the address linked by A8 and snapshotted in the escrow).
2. The freelancer asks for a payout. The marketplace checks: freelancer of the contract; contract on the Avalanche rail;
   adapter enabled; partner capability for the exact `(chainId, token contract)` the gateway's verified manifest names; escrow
   `RELEASED` on chain; linked wallet equals the escrow's freelancer. The partner returns a deposit address (and, if it quotes
   any, a fee or rate, stored as reported).
3. The backend builds `transfer(deposit, amount)` on the token; the freelancer's wallet signs and sends it. **FreelaX holds
   no key for it and never sends it.**
4. The gateway proves the transfer read-only from the receipt (`GET /api/v1/avalanche/token-transfers/{hash}`): the
   transaction targets the manifest token, succeeded, and its logs hold **exactly one** `Transfer(from, deposit, amount)` of
   that token; finality as for every other hash (LOCAL: confirmations; Fuji: `finalized` tag).
5. Only then is the partner asked for its fiat payout status.

## 3. State machine (`AvalanchePayoutStatus`)
`AWAITING_USER_TRANSFER -> TRANSFER_SUBMITTED -> TRANSFER_CONFIRMED -> FIAT_PAYOUT_PENDING -> FIAT_PAYOUT_CONFIRMED`;
`TRANSFER_SUBMITTED -> TRANSFER_FAILED -> TRANSFER_SUBMITTED` (a new hash after a definitive on-chain failure);
`TRANSFER_CONFIRMED | FIAT_PAYOUT_PENDING -> FIAT_PAYOUT_FAILED`. Terminal: `FIAT_PAYOUT_CONFIRMED`, `FIAT_PAYOUT_FAILED`.
Separate fields: `transfer_confirmed_at` (on chain) and `fiat_confirmed_at` (partner). The API exposes
`onChainTransferConfirmed` and `fiatPayoutConfirmed` as two booleans; neither implies the other.

| Gateway answer for the recorded hash | Effect |
|---|---|
| FINALIZED + MATCHED | TRANSFER_CONFIRMED |
| CONFIRMED (not final), PENDING, UNKNOWN + NO_RECEIPT | stays TRANSFER_SUBMITTED (absence proves nothing) |
| REVERTED | TRANSFER_FAILED `TRANSFER_REVERTED` |
| FINALIZED + TRANSFER_NOT_FOUND / AMOUNT_MISMATCH / AMBIGUOUS | TRANSFER_FAILED `TRANSFER_<match>` |
| UNKNOWN + TRANSFER_NOT_FOUND (receipt of a non-token target) | TRANSFER_FAILED |
| answer for another chain id or token | refused, record unchanged |
| gateway outage | exception, record unchanged (retry) |

Replay and reuse: one payout per `(network, milestone)`; one payout per `(network, transfer hash)`; one row per partner
order (V8 unique keys); a rejected hash cannot be submitted again for the same payout; terminal states never move. A
partner change between order and fiat check is refused (`PARTNER_CHANGED`), never silently asked of another partner.

## 4. Partners
| `avalanche-payout.partner` | Behaviour |
|---|---|
| `none` (default) | supports nothing: every request returns `UNSUPPORTED_RAIL` / `PARTNER_NOT_CONFIGURED` |
| `local-mock` | **labelled mock**, allowed only with `avalanche-gateway.network=avalanche-local` (startup fails otherwise) and only when the gateway reports LOCAL_SIMULATION (request refused otherwise); supports only chain 31337 and the one token in `local-mock-token-address`; quotes no fee or rate; reports fiat PAID with a `SIMULATED-PAYOUT-` reference |
| anything else | startup fails: no real partner exists in this codebase |
`avalanche-payout.enabled` defaults to `false` (`UNSUPPORTED_RAIL` / `AVALANCHE_PAYOUT_DISABLED`). A contract on another rail
gets `UNSUPPORTED_RAIL` / `CONTRACT_NOT_ON_AVALANCHE_RAIL` and the gateway is not even called.

## 5. Not decided here (owner or external)
- **Fees:** none computed. A partner-reported fee is stored verbatim; FreelaX platform fees on the Avalanche rail are an
  owner/business decision (D-B, C3).
- **Tax / MISA:** `taxTreatment = NOT_DEFINED`; no tax record is created for an Avalanche payout. Tax rules are a legal and
  owner decision.
- **Real partner:** needs a partner that supports Circle USDC on Avalanche C-Chain for the target network, a contract and
  credentials, and owner authorization; and on Fuji, a human-signed flow (M2: no backend Fuji signer is involved anyway,
  since the user signs).
- **Custody:** the design creates none. Whether the business wants the freelancer to off-ramp through FreelaX's partner at all
  is the D-B question recorded in L-017; the adapter is default-off until that is answered.

## 6. Code and tests
- Gateway (Java 21): `TokenTransferMatcher` (pure), `ChainClient.receiptLogs`, `EscrowActionService.verifyTokenTransfer`,
  `GET /token-transfers/{hash}`. Tests: `TokenTransferMatcherTest` (3), `AvalancheApiTest.aUsersOwnTokenTransferIsVerifiedFromTheReceiptLogs`
  (real Anvil: matched, amount mismatch, wrong recipient and sender, reverted, non-token target, unknown hash, input errors).
- Marketplace (Java 17, additive, package `payout.avalanche`): V8 `avalanche_payout_requests`; `AvalanchePayoutStatus`,
  `AvalanchePayoutRequest`, repository, `AvalanchePayoutPartner` port, `DisabledAvalanchePayoutPartner`,
  `LocalMockAvalanchePayoutPartner`, `AvalanchePayoutConfiguration`, `AvalanchePayoutProperties`, `AvalanchePayoutService`,
  `AvalanchePayoutController` (`/api/v1/avalanche/payouts`), `AvalancheClient.verifyTokenTransfer`. Tests:
  `AvalanchePayoutStatusTest`, `AvalanchePayoutPartnerTest`, `AvalanchePayoutServiceTest`, opt-in `AvalancheV8MySqlIT`.
- Frontend: `AvalanchePayoutSection` in the escrow panel (freelancer, released escrow only); sends the backend-built transfer
  from the released wallet only; shows both confirmations separately. Tests: `AvalanchePayoutSection.test.tsx`.
- Live LOCAL run: A2 harness flow 1b (`phase-2/a2-local-stack`).
Counts are recorded in the execution log (L-020).

> **Dated note (2026-11-05):** the "Tax / MISA" line above is superseded for new payouts: by owner decision the adapter records
> the platform fee and the taxable income with the existing Solana-rail rules, labelled `SOLANA_RAIL_RULES_SIMULATED` (V9,
> `SolanaRailFeeTaxRules`). No withheld tax is computed and no MISA record is created for this rail. Payouts created before V9
> keep `NOT_DEFINED`. A real partner is out of scope (simulation only).
