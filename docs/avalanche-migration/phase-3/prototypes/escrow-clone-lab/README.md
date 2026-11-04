# P3-C6: factory/clone measurement (LOCAL_SIMULATION only, O-2)

**Measurement only.** O-2 (owner, 2026-11-03): the shared immutable escrow remains the baseline; a factory/clone prototype is
permitted exclusively in LOCAL_SIMULATION. This folder has no deployment script, no manifest and no Fuji profile, lives
outside `avalanche-contracts/src`, and does not change the baseline (that tree is untouched). Engineering decided under the
Lead's delegation; R6 is not owner approved.

Run: `docker run --rm -v "$PWD:/repo" -w /repo/docs/avalanche-migration/phase-3/prototypes/escrow-clone-lab ghcr.io/foundry-rs/foundry:v1.8.7 "forge test -vv"`
(uses the dependencies installed in `avalanche-contracts/lib`). Output: `LOCAL_SIMULATION-clone-lab-20261104.txt` (4 passed).

## Results (Foundry 1.8.7, MockUSDC, 2026-11-04)
| Measure | Gas |
|---|---|
| EIP-1167 clone vault: create + initialize, per escrow | 123,573 |
| deposit into the clone vault | 62,268 |
| payout from the clone vault | 71,755 |
| shared baseline `fund` (median, `phase-2/evidence/forge-gas-report-escrow.txt`) | about 225,000 |

**Cost:** physical per-escrow custody adds a one-off creation cost of about 124k gas per escrow (roughly +55 % on the
baseline funding transaction), plus an extra external call on every payout in a fully integrated design (not measured: the
lab vault is not wired into the escrow logic).

**Blast radius:** a blacklist on one clone vault blocks only that escrow, which keeps its funds (stuck and reported, O-6),
while other escrows pay out normally (`test_blastRadius_aBlacklistedVaultBlocksOnlyItsOwnEscrow`). A blacklist on a shared
holder blocks every escrow's release and refund (`test_blastRadius_aBlacklistedSharedHolderBlocksEveryEscrow`): the
`ESCROW_BLACKLISTED` case that P3-C4 reports for the baseline.

## Reading for the owner (no decision taken here)
The trade-off is higher per-escrow gas and more deployed contracts against a smaller blast radius for issuer actions on the
escrow address itself. The baseline stays as decided (O-2). These numbers are LOCAL measurements on a mock token, not Fuji or
Mainnet costs.
