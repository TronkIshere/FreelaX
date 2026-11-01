# P2-M7 validation report: deployment script, environment manifest and the submission allow-list

Date: 2026-11-02. Scope source: [02 contract plan, P2-M7](02-contract-implementation-plan.md) and [05 environment plan](05-environment-and-signer-safety-plan.md) (sections 2 step 5, 3, 5). The prompt's M7 ("business integration") and M10 ("Fuji validation") are not the repository's M7; the repository roadmap is followed and those themes are covered in M9 and M12.
**No deployment to Fuji; no Fuji transaction, signature or backend signer; no Mainnet contact; no real funds.** The only deployment performed was to a local Anvil with its unlocked account.

## 1. Delivered
| Item | Detail |
|---|---|
| `script/Deploy.s.sol` | needs `DEPLOY_ENVIRONMENT`, `ARBITER`, `AUTH_SIGNER` (optional `PAUSER`); **refuses every chain except 31337 and 43113 and names Mainnet explicitly** (43114 and 1 revert with `MainnetForbidden`); the environment is explicit because a local fork of Fuji reports 43113; LOCAL_SIMULATION deploys `MockUSDC`, AVALANCHE_FUJI uses the Circle Fuji USDC and never a mock; the escrow admin is the broadcaster; the script holds **no key**: the signer is whatever forge is given (an unlocked Anvil account locally, a human-operated wallet for Fuji) |
| `scripts/deploy-local.sh` | one command against Anvil using its unlocked account; no key is passed or stored |
| Manifest | `deployments/<ENV>.json` (addresses, environment, chain id, token kind, start block, contract version); ignored by git for LOCAL; documented in `deployments/README.md` |
| `DeploymentManifest`, `ManifestVerifier` | fail-closed: environment and chain id equal the verified endpoint; asset allow-list (Circle Fuji USDC only on 43113, mock only in LOCAL_SIMULATION, AVALANCHE_FUJI accepts only the Circle token) and **deny-list** (Mainnet USDC and USDC.e addresses, refused everywhere); code present at escrow and token; the escrow's pinned token equals the manifest's; the token has 6 decimals and the symbol `USDC` |
| Submission allow-list | `SubmissionService` accepts only the manifest's escrow and token as `to` (`TARGET_NOT_ALLOWED`, no record, no nonce). **Submission now refuses to start without a verified manifest** (`MANIFEST_REQUIRED`). Closes the M3 limitation that a signer could be asked to call any address |
| Scanner wiring | the scanner takes the escrow address and start block from the verified manifest (a different configured address is `SCAN_CONTRACT_MISMATCH`) and registers the real escrow events (closes the M4 limitation of an empty decoder) |
| Compose | `AVALANCHE_DEPLOYMENT_MANIFEST` (blank default) and a read-only mount of `avalanche-contracts/deployments` |

## 2. Acceptance and results
| Criterion | Evidence | Result |
|---|---|---|
| Anvil deployment reproducible from one command | `scripts/deploy-local.sh` run for real against an Anvil container: succeeded, manifest produced | PASS |
| Manifest consumed by the gateway | the manifest produced by that real forge run verifies against its Anvil (`theManifestProducedByTheRealForgeScript...`, run with `AVALANCHE_TEST_MANIFEST`); also real in-test deployments | PASS |
| Script reverts on any chain id other than 31337 and 43113, 43114 explicit | `DeployGuardTest` (5 tests): Mainnet ids refused in both environments, per-environment chain rules, unknown or empty environment refused, `run()` on chain 43114 reverts before deploying anything, local run grants the signer role, pins the token and writes the manifest | PASS |
| No keys in scripts or repo | script reads addresses only; secret scan of changed files clean | PASS |
| Gateway refuses a manifest of another chain or environment, a deny-listed token, a wrong pinned token, missing code | `ManifestVerifierTest` (5, network-free) and `ManifestIntegrationTest` (live); Spring startup refusals (`MANIFEST_REQUIRED`, `MANIFEST_CHAIN_MISMATCH`, `ASSET_DENY_LISTED`, `SCAN_CONTRACT_MISMATCH`) | PASS |
| Submission limited to the manifest contracts | `submissionIsRestrictedToTheManifestContracts` (case-insensitive match; a refused target creates no record and consumes no nonce) | PASS |
| **Fuji deployment** | **BLOCKED**: needs a funded wallet and a human-operated signing procedure; exact manual procedure documented in `deployments/README.md` | BLOCKED |
Test totals: Foundry 54 passed, 0 failed, 1 skipped (fork suite off by default); gateway 98 run, 97 passed, 0 failed, 1 skipped (optional Fuji test); Solana baseline 6/6 plus 10/10 unchanged.

## 3. Limitations and decisions needed
1. **Fuji deployment and smoke transactions remain BLOCKED** until the owner defines a human-operated signing procedure (a wallet or hardware device that holds Fuji AVAX). The script and manifest verification are ready for it.
2. **LOCAL address churn:** development signers are ephemeral, so a new gateway process needs a deployment (or role grants) for its current signer addresses. The tests automate this with Anvil's unlocked account; a gateway-driven local deployment (contract creation through the submission pipeline) is not built and would need `to`-less transactions under review. For demonstrations, run the gateway and the one-command deployment with the logged signer addresses.
3. `startBlock` in the manifest is the head observed before broadcast, so the scanner starts at or before the first event.
4. The deny-list holds the two known Mainnet-network token addresses (no official USDC.e source was found in Phase 1); any other unknown token on Fuji is refused because Fuji accepts only the Circle address.
