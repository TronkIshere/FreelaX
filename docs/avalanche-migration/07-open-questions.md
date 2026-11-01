# 07. Open questions and unverified assumptions

Blocking (answer before Phase 1) are marked **B**.

## Business and architecture decisions pending
1. **B** Why Avalanche (partnership, user base, grant, cost)? The technical comparison in [02](02-solana-vs-avalanche.md) shows engineering benefits only.
2. **B** Is per-job fund isolation legally or custodially required (would favour a factory/clone pattern)? Default proposal: shared immutable escrow.
3. **B** Replace backend co-signature on fund/submit/revision with an operator-only function, an in-contract EIP-712 backend authorization, or drop it? Today's program requires the marketplace authority to co-sign.
4. **B** Should the 72h window, one extension, 2 revisions, dispute timing and auto-claim remain fully on-chain (current behaviour) or partly off-chain? Default: keep on-chain to preserve behaviour.
5. Should users pay AVAX gas, or should a relayer/sponsor be built (Solana currently sponsors fees)?
6. Can the on-chain `resolve` authority be a multisig/KMS role, and who holds the admin multisig?
7. New rail literal (for example `AVALANCHE_ESCROW`) vs reuse of `UNIFIED_USDC_PAYOUT` plus a chain column?
8. Is the legacy invoice/off-ramp (Anchor invoice, rate, withdrawal) path needed on Avalanche, or does the off-ramp concept move off-chain? Needs SQL evidence and product input.
9. How are in-flight Solana escrows handled at cutover (proposal: they finish on Solana)?
10. What replaces the mock on-ramp on Avalanche (Fuji test USDC faucet only)? Real on-ramp is not in the repo.

## Unverified facts (from [01](01-avalanche-technical-research.md))
- Public RPC rate limits and `eth_getLogs` block range.
- Whether `newHeads` fires at acceptance or execution after Helicon.
- Current EVM fork on C-Chain (Cancun vs later) and its effect on compiler settings.
- ACP-283 status; Helicon page "Scheduled" vs active wording.
- Fuji AVAX faucet amounts/limits; current Snowtrace/Routescan status and verification flow.
- Exact FiatToken version behind mainnet and Fuji USDC proxies; explorer attribution.
- Official source for the USDC.e address (the address in 01 is recalled, not verified).
- web3j 6.x compatibility with Java 21 and Spring Boot 3.5.16; why Maven Central and GitHub release dates differ.
- KMS to web3j signing recipe; Vault secp256k1 support; AvalancheGo self-hosting requirements; current Slither version.
- OpenZeppelin/Solidity/EIP pages were not fetched; escrow-pattern guidance is reasoning.
- Core Wallet and `wallet_addEthereumChain` specifics; Reown package names.

## Repository unknowns
- Which rails hold production data (ADR SQL queries not yet run).
- Partner rail's path through `SettlementDownstreamService`.
- misa-backend migrations; payment-backend internals.
- UI caller of `/solana/wallet-link/challenge|verify`.

## Experiments required (Phase 1)
- Fuji: send tx, measure time to accepted, receipt, `finalized`; observe `eth_getLogs` before execution; test `newHeads` vs `newAcceptedTransactions`.
- Fork test against Fuji USDC: approve/transferFrom, permit, blacklist behavior (if testable).
- KMS secp256k1 signing to a valid Fuji tx.
- Public RPC and chosen provider rate-limit probing.
- accepted-then-reverted transaction handling.

## Questions to resolve before Phase 1
Items marked **B** above, plus approval of this plan, access for read-only SQL evidence, and accounts for RPC provider and KMS.
